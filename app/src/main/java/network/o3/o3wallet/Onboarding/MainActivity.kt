package network.o3.o3wallet.Onboarding

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.view.ViewPager
import android.widget.Button
import android.widget.Toast
import co.getchannel.channel.Channel
import co.getchannel.channel.callback.ChannelCallback
import com.akexorcist.localizationactivity.ui.LocalizationActivity
import com.crashlytics.android.Crashlytics
import com.google.zxing.integration.android.IntentIntegrator
import io.fabric.sdk.android.Fabric
import network.o3.o3wallet.Account
import network.o3.o3wallet.BuildConfig
import network.o3.o3wallet.R
import network.o3.o3wallet.SelectingBestNode
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.yesButton

class MainActivity : LocalizationActivity() {
    private lateinit var viewPager: ViewPager
    private lateinit var pagerAdapter: LandingPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            Fabric.with(this, Crashlytics())
        }
        setContentView(R.layout.onboarding_activity_main)

        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener { loginTapped() }

        val createNewWalletButton = findViewById<Button>(R.id.createNewWallet)
        createNewWalletButton.setOnClickListener { createWalletTapped() }

        val viewPager = findViewById<ViewPager>(R.id.viewPager)
        pagerAdapter = LandingPagerAdapter(supportFragmentManager)
        viewPager.adapter = pagerAdapter

        Channel.setupApplicationContextWithApplicationKey(baseContext,"app_gUHDmimXT8oXRSpJvCxrz5DZvUisko_mliB61uda9iY", object : ChannelCallback {
            override fun onSuccess() {}
            override fun onFail(message: String) {}
        })

        if (Account.isEncryptedWalletPresent()) {
            authenticateEncryptedWallet()
        }
    }

    fun authenticateEncryptedWallet() {
        val mKeyguardManager =  getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!mKeyguardManager.isKeyguardSecure) {
            // Show a message that the user hasn't set up a lock screen.
            Toast.makeText(this,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Screenlock' to set up a lock screen",
                    Toast.LENGTH_LONG).show()
            return
        } else {
            val intent = mKeyguardManager.createConfirmDeviceCredentialIntent(null, null)
            if (intent != null) {
                startActivityForResult(intent, 1)
            }
        }
    }

    fun createWalletTapped() {
        if (Account.isEncryptedWalletPresent()) {
            alert (resources.getString(R.string.existing_key_detected)) {
                yesButton {
                    authenticateReplaceWallet()
                }
                noButton {

                }
            }.show()
        } else {
            Account.createNewWallet()
            val intent = Intent(this@MainActivity, CreateWalletActivity::class.java)
            startActivity(intent)
        }
    }

    fun authenticateReplaceWallet() {
        val mKeyguardManager =  getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!mKeyguardManager.isKeyguardSecure) {
            // Show a message that the user hasn't set up a lock screen.
            Toast.makeText(this, resources.getString(R.string.no_passcode_setup), Toast.LENGTH_LONG).show()
            return
        } else {
            val intent = mKeyguardManager.createConfirmDeviceCredentialIntent("Log in to your existing wallet", null)
            if (intent != null) {
                startActivityForResult(intent, 0)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents == null) {

        } else {
            if (resultCode == -1) {
                Account.restoreWalletFromDevice()
                val intent = Intent(this, SelectingBestNode::class.java)
                startActivity(intent)
            }
        }
    }

    fun loginTapped() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}
