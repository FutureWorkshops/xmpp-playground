package com.futureworkshops.xmpp

data class Credentials(val username: String, val password: String) {

    companion object {
        val ARIS = Credentials("aris@localhost", "0olON-O4K8qIitSB")
        val TEST = Credentials("test@localhost", "0olON-O4K8qIitSB")
        val IGOR = Credentials("igor@localhost", "0olON-O4K8qIitSB")
    }
}

