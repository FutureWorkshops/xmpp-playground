package com.futureworkshops.xmpp

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_receive.*
import org.jivesoftware.smack.chat2.ChatManager

class ReceiveActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)


        val chatManager = ChatManager.getInstanceFor(ConnectionKeeper.connection)


        chatManager.addIncomingListener { from, message, chat ->
            latestMessageTextView.post {
                latestMessageTextView.text = "${from} says: ${message.body}"
            }

        }
    }
}
