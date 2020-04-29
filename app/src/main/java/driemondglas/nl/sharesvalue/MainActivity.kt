package driemondglas.nl.sharesvalue

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.SystemClock
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.koushikdutta.ion.Ion
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.text.NumberFormat
import java.util.*
import kotlin.concurrent.schedule

private lateinit var myPreferences: SharedPreferences

fun View.enabled(isEnabled: Boolean) {
    alpha = if (isEnabled) 1f else 0.3f   // high transparency looks like greyed out
    isClickable = isEnabled
}

class MainActivity : AppCompatActivity() {
    private var qtyUSD = 0
    private var qtyHPE = 0
    private var qtyAgilent = 0


    private var priceHPE = 0f
    private var priceAgilent = 0f
    private var rateUSD = 1f

    private var savedHPE = 0f
    private var savedAgilent = 0f
    private var savedUSD = 0f
    private var savedDate = ""

    private val usdFormat = NumberFormat.getCurrencyInstance(Locale("en", "US"))
    private val euroFormat = NumberFormat.getCurrencyInstance(Locale("nl", "NL"))
    private val numbFormat = NumberFormat.getNumberInstance(Locale("en", "US"))
    private val pctFormat = NumberFormat.getPercentInstance(Locale("en", "US"))
    private var sdf: SimpleDateFormat = SimpleDateFormat("dd-MM-`yy HH:mm")

    private var probeState = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myPreferences = applicationContext.getSharedPreferences("SharesPrefKey", Context.MODE_PRIVATE)

        btn_save.setOnClickListener { saveLast() }

        btn_refresh.setOnClickListener {
            /* disable this button until new data available */
            btn_refresh.enabled(false)

            /* get saved values from shared preferences */
            retrieveSaved()

            /* get actual values from alpha vantage server */
            probeShares()
        }

        btn_refresh.enabled(false)

        numbFormat.minimumFractionDigits = 4
        pctFormat.minimumFractionDigits = 2

        /* get saved values from shared preferences */
        retrieveSaved()

        /* get actual values from alpha vantage server */
        probeShares()
    }

    private fun retrieveSaved() {
        /* retrieve last saved values from shared preferences */
        with(myPreferences) {
            qtyUSD = getInt("qtyCash", 1)
            qtyHPE = getInt("qtyHPE", 1)
            qtyAgilent = getInt("qtyAgilent", 1)

            savedHPE = getFloat("HPE", 0f)
            savedAgilent = getFloat("A", 0f)
            savedUSD = getFloat("USD", 0f)

            savedDate = getString("date", sdf.format(Date())) ?: "no date set"
        }
        txt_qty_agilent.setText(qtyAgilent.toString())
        txt_qty_hpe.setText(qtyHPE.toString())
        txt_qty_usd.setText(qtyUSD.toString())
    }

    private fun saveLast() {
        qtyUSD = txt_qty_usd.text.toString().toInt()
        qtyHPE = txt_qty_hpe.text.toString().toInt()
        qtyAgilent = txt_qty_agilent.text.toString().toInt()
        savedDate = sdf.format(Date())
        myPreferences.edit()
            .putString("date", savedDate)
            .putInt("qtyCash", qtyUSD)
            .putInt("qtyHPE", qtyHPE)
            .putInt("qtyAgilent", qtyAgilent)
            .putFloat("HPE", priceHPE)
            .putFloat("A", priceAgilent)
            .putFloat("USD", rateUSD)
            .apply()

        savedHPE = priceHPE
        savedAgilent = priceAgilent
        savedUSD = rateUSD

        refreshTable()
    }

    private fun probeShares() {
        probeState = 0

        Ion.with(this)
            .load("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=HPE&apikey=QM6DLWTO531JNLCC")
            .asString()
            .setCallback { ex, result ->
                Log.d("hvr", "exception:\n$ex\n\nresult:\n$result")
                if (result.contains("Global Quote", false)) {
                    priceHPE = (getQuoteItem(result, "price")).toFloat()
                    probeState++
                    if (probeState == 3) {
                        disableButton1Minute()
                        refreshTable()
                    }
                } else if (result.contains("Note", false)) {
                    Toast.makeText(this, getNote(result), Toast.LENGTH_LONG).show()
                } else {
                    Log.d("hvr", "unexpected result: $result\n$ex")
                }
            }

        Ion.with(this)
            .load("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=A&apikey=QM6DLWTO531JNLCC")
            .asString()
            .setCallback { ex, result ->
                if (result.contains("Global Quote", false)) {
                    priceAgilent = (getQuoteItem(result, "price")).toFloat()
                    probeState++
                    if (probeState == 3) {
                        disableButton1Minute()
                        refreshTable()
                    }
                } else if (result.contains("Note", false)) {
                    Toast.makeText(this, getNote(result), Toast.LENGTH_LONG).show()
                } else {
                    Log.d("hvr","unexpected result: $result\n$ex")
                }
            }

        Ion.with(this)
            .load("https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=USD&to_currency=EUR&apikey=QM6DLWTO531JNLCC")
            .asString()
            .setCallback { ex, result ->
                if (result.contains("Realtime Currency Exchange Rate", false)) {
                    rateUSD = getCurrencyItem(result).toFloat()
                    probeState++
                    if (probeState == 3) {
                        disableButton1Minute()
                        refreshTable()
                    }
                } else if (result.contains("Note", false)) {
                    Toast.makeText(this, getNote(result), Toast.LENGTH_LONG).show()
                } else {
                    Log.d("hvr", "unexpected result: $result\n$ex")
                }
            }
    }

    @Suppress("SameParameterValue")
    private fun getQuoteItem(result: String, item: String): String {
        /*
            json result output looks like
            {
                "Global Quote": {
                    "01. symbol": "MSFT",
                    "02. open": "119.5000",
                    "03. high": "119.5890",
                    "04. low": "117.0400",
                    "05. price": "117.0500",
                    "06. volume": "33624528",
                    "07. latest trading day": "2019-03-22",
                    "08. previous close": "120.2200",
                    "09. change": "-3.1700",
                    "10. change percent": "-2.6368%"
                }
            }
        */

        val json = JSONObject(result)  //entire result
        val jsonQuote = json.getJSONObject("Global Quote")

        return when (item) {
            "symbol" -> jsonQuote.getString("01. symbol")
            "price" -> jsonQuote.getString("05. price")
            "previous" -> jsonQuote.getString("08. previous close")
            "change" -> jsonQuote.getString("10. change percent")
            else -> "Item $item Not Found"
        }
    }

    private fun getCurrencyItem(callBackResult: String): String {
        /*
            {
                "Realtime Currency Exchange Rate": {
                "1. From_Currency Code": "EUR",
                "2. From_Currency Name": "Euro",
                "3. To_Currency Code": "USD",
                "4. To_Currency Name": "United States Dollar",
                "5. Exchange Rate": "1.13058220",
                "6. Last Refreshed": "2019-03-23 20:56:37",
                "7. Time Zone": "UTC"
            }
        */

        val json = JSONObject(callBackResult)       //entire result
        val jsonCurrency = json.getJSONObject("Realtime Currency Exchange Rate")
        return jsonCurrency.getString("5. Exchange Rate")
    }

    private fun getNote(callBackResult: String): String {
        /* json result output looks something like:
            {
                "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute and 500 calls per day. Please visit https://www.alphavantage.co/premium/ if you would like to target a higher API call frequency."
            } */
        Log.d("hvr","Result $callBackResult")
        val jsonNote = JSONObject(callBackResult)
        return jsonNote.getString("Note")
    }

    private fun disableButton1Minute() {
        runOnUiThread {
            /* disable the refresh function for 1 minute to comply with alpha vantage per minute maximum */
            btn_refresh.enabled(false)
            view_timer.visibility = View.VISIBLE
            Timer().schedule(60000) {
                view_timer.stop()
                view_timer.visibility = View.INVISIBLE
                btn_refresh.enabled(true)
            }
            view_timer.isCountDown = true
            view_timer.base = SystemClock.elapsedRealtime() + 59000
            view_timer.start()
        }
    }

    private fun refreshTable() {

            /* SHOW SAVED VALUES */
            txt_saved_date.text = savedDate
            txt_saved_hpe.text = usdFormat.format(savedHPE)
            txt_saved_agilent.text = usdFormat.format(savedAgilent)
            txt_saved_usd.text = numbFormat.format(savedUSD)

            /* SHOW DATE */
            txt_refresh_date.text = sdf.format(Date())
            txt_refresh_date.setTextColor(Color.BLUE)

            /* SHOW HPE LINE*/
            txt_price_hpe.text = usdFormat.format(priceHPE)
            txt_price_hpe.setTextColor(Color.BLUE)

            /* difference from saved */
            var diffPct = (priceHPE - savedHPE) / savedHPE
            txt_pct_hpe.text = pctFormat.format(diffPct)
            txt_pct_hpe.setTextColor(colorOfValue(diffPct))

            val diffEurHPE = qtyHPE * (priceHPE - savedHPE) * rateUSD
            txt_eur_hpe.text = euroFormat.format(diffEurHPE)
            txt_eur_hpe.setTextColor(colorOfValue(diffEurHPE))

            /* SHOW AGILENT LINE */

            txt_price_agilent.text = usdFormat.format(priceAgilent)
            txt_price_agilent.setTextColor(Color.BLUE)

            /* difference from saved */
            diffPct = (priceAgilent - savedAgilent) / savedAgilent
            txt_pct_agilent.text = pctFormat.format(diffPct)
            txt_pct_agilent.setTextColor(colorOfValue(diffPct))

            val diffEurAgilent = qtyAgilent * (priceAgilent - savedAgilent) * rateUSD
            txt_eur_agilent.text = euroFormat.format(diffEurAgilent)
            txt_eur_agilent.setTextColor(colorOfValue(diffEurAgilent))

            /* SHOW USD LINE */
            txt_rate_usd.text = numbFormat.format(rateUSD)
            txt_rate_usd.setTextColor(Color.BLUE)

            /* difference from saved */
            diffPct = (rateUSD - savedUSD) / savedUSD
            txt_pct_usd.text = pctFormat.format(diffPct)
            txt_pct_usd.setTextColor(colorOfValue(diffPct))

            val diffEurUSD = qtyUSD * (rateUSD - savedUSD) * rateUSD
            txt_eur_usd.text = euroFormat.format(diffEurUSD)
            txt_eur_usd.setTextColor(colorOfValue(diffEurUSD))

            /* SHOW TOTALS LINE */
            sum_all_eur.text = euroFormat.format((qtyUSD + qtyHPE * priceHPE + qtyAgilent * priceAgilent) * rateUSD)
            sum_div_eur.text = euroFormat.format(diffEurHPE + diffEurAgilent + diffEurUSD)
            sum_div_eur.setTextColor(colorOfValue(diffEurHPE + diffEurAgilent + diffEurUSD))
    }

    private fun colorOfValue(waarde: Float): Int {
        return when {
            waarde < 0f -> Color.RED
            waarde > 0f -> Color.BLUE
            else -> Color.BLACK
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /* Inflate the menu; this adds menu items to the zorba action bar. */
        menuInflater.inflate(R.menu.menu_shares, menu)
        menu.findItem(R.id.menu_showqty).isChecked = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Handle action bar item clicks here. */
        when (item.itemId) {
            /* speech on/off */
            R.id.menu_showqty -> {
                val showQty = !item.isChecked
                item.isChecked = !item.isChecked
                txt_qty_usd.visibility = if (showQty) View.VISIBLE else View.INVISIBLE
                txt_qty_hpe.visibility = if (showQty) View.VISIBLE else View.INVISIBLE
                txt_qty_agilent.visibility = if (showQty) View.VISIBLE else View.INVISIBLE
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }
}



