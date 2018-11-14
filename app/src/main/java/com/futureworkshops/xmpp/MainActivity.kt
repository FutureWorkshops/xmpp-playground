package com.futureworkshops.xmpp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.futureworkshops.xmpp.Credentials.Companion.ARIS
import com.futureworkshops.xmpp.Credentials.Companion.IGOR
import com.futureworkshops.xmpp.Credentials.Companion.TEST
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val connectionManager = ConnectionManager()


        arisLoginButton.setOnClickListener {
            connectionManager.login(ARIS)
        }

        testLoginButton.setOnClickListener {
            connectionManager.login(TEST)
        }

        igorLoginButton.setOnClickListener {
            connectionManager.login(IGOR)
        }

    }



}

