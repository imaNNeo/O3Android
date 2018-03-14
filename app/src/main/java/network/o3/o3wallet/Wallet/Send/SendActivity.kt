package network.o3.o3wallet.Wallet.Send

import android.app.Activity
import android.app.Fragment
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v4.app.NavUtils
import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import network.o3.o3wallet.API.NEO.NeoNodeRPC
import android.widget.*
import com.akexorcist.localizationactivity.ui.LocalizationActivity
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import neoutils.Neoutils
import neoutils.Neoutils.parseNEP9URI
import neoutils.SeedNodeResponse
import network.o3.o3wallet.API.NEO.AccountAsset
import network.o3.o3wallet.API.NEO.AccountState
import network.o3.o3wallet.API.NEO.Balance
import network.o3.o3wallet.Account
import network.o3.o3wallet.PersistentStore
import network.o3.o3wallet.R
import network.o3.o3wallet.Wallet.toast
import network.o3.o3wallet.Wallet.toastUntilCancel
import org.jetbrains.anko.alert
import org.jetbrains.anko.coroutines.experimental.bg
import org.jetbrains.anko.custom.onUiThread
import org.jetbrains.anko.yesButton

class SendActivity : LocalizationActivity() {

    lateinit var addressTextView: TextView
    lateinit var amountTextView: TextView
    lateinit var noteTextView: TextView
    lateinit var selectedAssetTextView: TextView
    lateinit var sendButton: Button
    lateinit var pasteAddressButton: Button
    lateinit var scanAddressButton: Button
    lateinit var view: View

    private var gasBalance: Balance? = null

    var isNativeAsset = true
    //if native asset this refers to assetid, otherwise tokenhash
    var assetID = NeoNodeRPC.Asset.NEO.assetID()
    var shortName = "NEO"

    public val ARG_REVEAL_SETTINGS: String = "arg_reveal_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_activity_send)

        this.title = resources.getString(R.string.send)
        view = findViewById<View>(R.id.root_layout)
        addressTextView = findViewById<EditText>(R.id.addressTextView)
        amountTextView = findViewById<EditText>(R.id.amountTextView)
        noteTextView = findViewById<EditText>(R.id.noteTextView)
        sendButton = findViewById<Button>(R.id.sendButton)
        pasteAddressButton = findViewById<Button>(R.id.pasteAddressButton)
        scanAddressButton = findViewById<Button>(R.id.scanAddressButton)
        selectedAssetTextView = findViewById<TextView>(R.id.selectedAssetTextView)

        amountTextView.keyListener = DigitsKeyListener.getInstance("0123456789")
        selectedAssetTextView.text = shortName.toUpperCase()
        addressTextView.afterTextChanged { checkEnableSendButton() }
        amountTextView.afterTextChanged { checkEnableSendButton() }
        selectedAssetTextView.setOnClickListener { displayAssets() }

        sendButton.isEnabled = false
        sendButton.setOnClickListener { sendTapped() }

        pasteAddressButton.setOnClickListener { pasteAddressTapped() }
        scanAddressButton.setOnClickListener { scanAddressTapped() }

        val extras = intent.extras
        val address = extras.getString("address")

        if (address != "") {
            addressTextView.text = address
            amountTextView.requestFocus()
            showFoundContact(address)
        }
        //get best node in the background here.
        getBestNode()

        //get account state in the background here to check if a user has GAS to perform sending token
        async(UI) {
            bg {
                NeoNodeRPC(PersistentStore.getNodeURL()).getAccountState(address = Account.getWallet()!!.address) {
                    runOnUiThread {
                        showAccountState(it)
                    }
                }
            }
        }
    }

    private fun showAccountState(data: Pair<AccountState?, Error?>): Unit {
        val error = data.second
        val accountState = data.first

        if (error != null) {
            return
        }
        for (balance in accountState!!.balances.iterator()) {
            //if only care about GAS here
            if (balance.asset.contains(NeoNodeRPC.Asset.GAS.assetID())) {
                this.gasBalance = balance
            }
        }
    }

    private fun gotBestNode(node: SeedNodeResponse) {
        PersistentStore.setNodeURL(node.url)
    }

    private fun getBestNode() {
        val nodes = "https://seed1.neo.org:10331," +
                "http://seed2.neo.org:10332," +
                "http://seed3.neo.org:10332," +
                "http://seed4.neo.org:10332," +
                "http://seed5.neo.org:10332," +
                "http://seed1.cityofzion.io:8080," +
                "http://seed2.cityofzion.io:8080," +
                "http://seed3.cityofzion.io:8080," +
                "http://seed4.cityofzion.io:8080," +
                "http://seed5.cityofzion.io:8080," +
                "http://seed1.o3node.org:10332," +
                "http://seed2.o3node.org:10332," +
                "http://seed3.o3node.org:10332"


        async(UI) {
            val data: Deferred<SeedNodeResponse> = bg {
                Neoutils.selectBestSeedNode(nodes)
            }
            gotBestNode(data.await())
        }
    }

    fun showFoundContact(address: String) {
        val contacts = PersistentStore.getContacts()
        val foundContact = contacts.find { it.address == address }
        val toLabel = findViewById<TextView>(R.id.sendToLabel)
        if (foundContact != null) {
            toLabel.text = "To: %s".format(foundContact.nickname)
        } else {
            toLabel.text = "To"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == R.id.home) {
            NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    private fun checkEnableSendButton() {
        showFoundContact(addressTextView.text.trim().toString())
        sendButton.isEnabled = (addressTextView.text.trim().count() > 0 && amountTextView.text.count() > 0)
    }

    private fun displayAssets() {
        val assetSelectorSheet = AssetSelectionBottomSheet()
        val assets = intent.extras.getSerializable("assets")
        if (assets == null) {
            assetSelectorSheet.assets = arrayListOf()
        } else {
            assetSelectorSheet.assets = assets as ArrayList<AccountAsset>
        }

        assetSelectorSheet.show(this.supportFragmentManager, assetSelectorSheet.tag)
    }

    fun updateSelectedAsset() {
        selectedAssetTextView.text = shortName
        if (shortName != "NEO") {
            amountTextView.keyListener = DigitsKeyListener.getInstance("0123456789.")
        } else {
            amountTextView.keyListener = DigitsKeyListener.getInstance("0123456789")
        }
    }

    private fun send() {
        //validate field
        val address = addressTextView.text.trim().toString()
        var amount = amountTextView.text.trim().toString().toDouble()

        if (amount == 0.0) {
            baseContext.toast(resources.getString(R.string.amount_must_be_nonzero))
            return
        }

        sendButton.isEnabled = false
        if (isNativeAsset) {
            sendNativeAsset(address, amount)
        } else {
            sendTokenAsset(address, amount)
        }

    }

    private fun sendNativeAsset(address: String, amount: Double) {
        val wallet = Account.getWallet()
        val toast = baseContext.toastUntilCancel(resources.getString(R.string.sending_in_progress))
        var toSendAsset: NeoNodeRPC.Asset
        if (shortName == "NEO") {
            toSendAsset = NeoNodeRPC.Asset.NEO
        } else {
            toSendAsset = NeoNodeRPC.Asset.GAS
        }

        NeoNodeRPC(PersistentStore.getNodeURL()).sendNativeAssetTransaction(wallet!!, toSendAsset, amount, address, null) {
            runOnUiThread {
                toast.cancel()
                val error = it.second
                val success = it.first
                if (success == true) {
                    baseContext!!.toast(resources.getString(R.string.sent_successfully))
                    Handler().postDelayed(Runnable {
                        finish()
                    }, 1000)
                } else {
                    this.checkEnableSendButton()
                    val message = resources.getString(R.string.send_error)
                    val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                    snack.setAction("Close") {
                        finish()
                    }
                    snack.show()
                }
            }
        }
    }

    private fun sendTokenAsset(address: String, amount: Double) {
        val wallet = Account.getWallet()
        val toast = baseContext.toastUntilCancel(resources.getString(R.string.sending_in_progress))

        NeoNodeRPC(PersistentStore.getNodeURL()).sendNEP5Token(wallet!!, assetID, wallet!!.address, address, amount) {
            runOnUiThread {
                toast.cancel()
                val error = it.second
                if (error != null) {
                    val message = resources.getString(R.string.send_error)
                    val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                    snack.setAction("Close") {
                        finish()
                    }
                    snack.show()
                } else {
                    val success = it.first
                    if (success == true) {
                        baseContext!!.toast(resources.getString(R.string.sent_successfully))
                        Handler().postDelayed(Runnable {
                            finish()
                        }, 1000)
                    } else {
                        this.checkEnableSendButton()
                        val message = resources.getString(R.string.send_error)
                        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                        snack.setAction("Close") {
                            finish()
                        }
                        snack.show()
                    }
                }

            }
        }
    }

    private fun sendTapped() {
        this.hideKeyboard()
        //validate field
        val address = addressTextView.text.trim().toString()
        var amount = amountTextView.text.trim().toString().toDouble()

        if (amount == 0.0) {
            baseContext.toast(resources.getString(R.string.amount_must_be_nonzero))
            return
        }
        //check if user has enough GAS to perform send token here.
        if (isNativeAsset == false) {
            if (this.gasBalance == null || this.gasBalance!!.value == 0.0) {
                //alert that a user need 0.00000001 gas to send token
                alert("You need at least 0.00000001GAS to send any token", "") {
                    yesButton {
                        addressTextView.requestFocus()
                    }
                }.show()
                return
            }
        }


        NeoNodeRPC(PersistentStore.getNodeURL()).validateAddress(address) {
            if (it.second != null || it?.first == false) {
                runOnUiThread {
                    alert(resources.getString(R.string.invalid_neo_address), resources.getString(R.string.error)) {
                        yesButton {
                            addressTextView.requestFocus()
                        }
                    }.show()
                }
            } else {
                runOnUiThread {
                    alert(resources.getString(R.string.send_confirmation, amount.toString(),
                            this.shortName.toUpperCase(), address)) {
                        positiveButton(resources.getString(R.string.send)) {
                            send()
                        }
                        negativeButton(resources.getString(R.string.cancel)) {

                        }
                    }.show()
                }
            }
        }
    }

    private fun pasteAddressTapped() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            val item = clip.getItemAt(0)
            addressTextView.text = item.text.toString()
        }
    }


    fun scanAddressTapped() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
        integrator.setPrompt(resources.getString(R.string.scan_prompt_qr_send))
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, resources.getString(R.string.cancelled), Toast.LENGTH_LONG).show()
            } else if (Neoutils.validateNEOAddress(result.contents.trim())) {
                addressTextView.text = result.contents.trim()
            } else try {
                val uri = parseNEP9URI(result.contents.trim())
                addressTextView.text = uri.to
                amountTextView.text = uri.amount.toString()
                isNativeAsset = true
                assetID = uri.assetID
                if (assetID.contains(NeoNodeRPC.Asset.NEO.assetID())) {
                    shortName = "NEO"
                } else {
                    shortName = "GAS"
                }
                updateSelectedAsset()

            } catch (e: Exception) {
            }
        }
    }
}

fun TextView.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }
    })
}


fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Fragment.hideKeyboard() {
    activity.hideKeyboard(view)
}

fun Activity.hideKeyboard() {
    hideKeyboard(if (currentFocus == null) View(this) else currentFocus)
}