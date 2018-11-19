package com.futureworkshops.xmpp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.futureworkshops.xmpp.Credentials.Companion.ARIS
import com.futureworkshops.xmpp.Credentials.Companion.IGOR
import com.futureworkshops.xmpp.Credentials.Companion.TEST
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private lateinit var connectionManager: ConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionManager = ConnectionManager()


        arisLoginButton.setOnClickListener {
            launch {
                login(ARIS)
                goToSendActivity()
            }
        }

        testLoginButton.setOnClickListener {
            launch {
                login(TEST)
                goToReceiveActivity()
            }
        }

        igorLoginButton.setOnClickListener {
            launch {
                login(IGOR)
            }
        }

    }

    private fun goToSendActivity() {
        startActivity(Intent(this, SendActivity::class.java))
    }

    private fun goToReceiveActivity() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private suspend fun login(credentials: Credentials) {
        val context = this
        val connection = connectionManager.login(credentials)
        ConnectionKeeper.connection = connection
        Toast.makeText(context, "Logged in successfully", Toast.LENGTH_SHORT).show()
    }

}

