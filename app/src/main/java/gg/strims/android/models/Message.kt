package gg.strims.android.models

data class Message(var privMsg: Boolean, var nick: String, var data: String, var timestamp: Long, var features: Array<String>) {

    constructor(): this(false,"", "", -1, arrayOf<String>())

//    private var privMsg: Boolean? = null
//
//    constructor(privMsg: Boolean, nick: String, data: String, timestamp: Long) : this(nick, data, timestamp){
//        this.privMsg = privMsg
//    }
}