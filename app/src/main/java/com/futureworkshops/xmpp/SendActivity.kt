package com.futureworkshops.xmpp

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_send.*
import org.jivesoftware.smack.chat2.ChatManager
import org.jxmpp.jid.impl.JidCreate

class SendActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)


        val chatManager = ChatManager.getInstanceFor(ConnectionKeeper.connection)
        val jid = JidCreate.entityBareFrom("test@localhost")
        val chat = chatManager.chatWith(jid)


        sendCannedButton.setOnClickListener {
            chat.send("This is a canned message")
        }


        sendCustomButton.setOnClickListener {
            val message = messageEditText.text
            if (message.isEmpty()) {
                Toast.makeText(this@SendActivity, "Come on, type something, don't be lazy", Toast.LENGTH_SHORT).show()
            } else {
                chat.send(message)
            }
        }

    }
}
