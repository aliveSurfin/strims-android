package gg.strims.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import io.ktor.util.KtorExperimentalAPI
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.chat_message.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.beust.klaxon.Klaxon
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.wss
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readBytes
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.send
import kotlinx.android.synthetic.main.chat_message.view.message
import kotlinx.android.synthetic.main.private_chat_message.view.*
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.net.URL
import java.util.*

@KtorExperimentalAPI
class ChatActivity : AppCompatActivity() {

    private val adapter = GroupAdapter<GroupieViewHolder>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        GlobalScope.launch {
            WSClient().onConnect()
        }

        sendMessageText.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (sendMessageText.text.isNotEmpty()) {
                    sendMessageButton.isEnabled = true
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (sendMessageText.text.isNotEmpty()) {
                    sendMessageButton.isEnabled = true
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (sendMessageText.text.isNotEmpty()) {
                    sendMessageButton.isEnabled = true
                }
            }
        })

        sendMessageText.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessageButton.performClick()
                return@OnKeyListener true
            }
            false
        })

        recyclerViewChat.adapter = adapter
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerViewChat.layoutManager = layoutManager
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (CurrentUser.user != null) {
            menu!!.findItem(R.id.chatLogin).isVisible = false
            menu.findItem(R.id.chatProfile).isVisible = true
            menu.findItem(R.id.chatSignOut).isVisible = true
            menu.findItem(R.id.chatOptions).isVisible = true
        } else {
            menu!!.findItem(R.id.chatLogin).isVisible = true
            menu.findItem(R.id.chatProfile).isVisible = false
            menu.findItem(R.id.chatSignOut).isVisible = false
            menu.findItem(R.id.chatOptions).isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.chatLogin -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            R.id.chatProfile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.chatOptions -> {
                startActivity(Intent(this, ChatOptionsActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ChatMessage(private val messageData: Message) : Item<GroupieViewHolder>() {
        override fun getLayout(): Int {
            return R.layout.chat_message
        }

        @SuppressLint("SetTextI18n", "SimpleDateFormat")
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (CurrentUser.options!!.ignoreList.contains(messageData.nick)) {
                return
            }
            val date = Date(messageData.timestamp)
            val time = if (date.minutes < 10) {
                "${date.hours}:0${date.minutes}"
            } else {
                "${date.hours}:${date.minutes}"
            }

            if (messageData.features.contains("bot")) {
                viewHolder.itemView.username.setTextColor(Color.parseColor("#FF2196F3"))
            }

            if (CurrentUser.user != null) {
                if (messageData.data.contains(CurrentUser.user!!.username)) {
                    viewHolder.itemView.setBackgroundColor(Color.parseColor("#001D36"))
                } else {
                    viewHolder.itemView.setBackgroundColor(Color.parseColor("#000000"))
                }
            }

            if (CurrentUser.options!!.greentext) {
                if (messageData.data.first() == '>') {
                    viewHolder.itemView.message.setTextColor(Color.parseColor("#789922"))
                } else {
                    viewHolder.itemView.message.setTextColor(Color.parseColor("#FFFFFF"))
                }
            }

            viewHolder.itemView.timestampMessage.text = time
            viewHolder.itemView.username.text = "${messageData.nick}:"
            viewHolder.itemView.message.text = messageData.data
        }
    }

    inner class PrivateChatMessage(private val messageData: Message) : Item<GroupieViewHolder>() {
        override fun getLayout(): Int {
            return R.layout.private_chat_message
        }

        @SuppressLint("SetTextI18n")
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (CurrentUser.options!!.ignoreList.contains(messageData.nick)) {
                return
            }
            val date = Date(messageData.timestamp)
            val time = if (date.minutes < 10) {
                "${date.hours}:0${date.minutes}"
            } else {
                "${date.hours}:${date.minutes}"
            }

            if (messageData.data.first() == '>') {
                viewHolder.itemView.message.setTextColor(Color.parseColor("#789922"))
            }

            viewHolder.itemView.timestampMessagePrivate.text = time
            viewHolder.itemView.usernamePrivate.text = messageData.nick
            viewHolder.itemView.messagePrivate.text = " whispered: ${messageData.data}"
        }
    }

    fun retrieveOptions() {
        val file = baseContext.getFileStreamPath("filename.txt")
        if (file.exists()) {
            val fileInputStream = openFileInput("filename.txt")
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            val stringBuilder = StringBuilder()
            var text: String? = null
            while ({ text = bufferedReader.readLine(); text }() != null) {
                stringBuilder.append(text)
            }
            CurrentUser.options = Klaxon().parse(stringBuilder.toString())
        } else {
            CurrentUser.options = Options()
        }
        Log.d("TAG", "${CurrentUser.options!!.greentext}, ${CurrentUser.options!!.emotes}")
    }

    private fun saveOptions() {
        val userOptions = CurrentUser.options
        val fileOutputStream: FileOutputStream
        try {
            fileOutputStream = openFileOutput("filename.txt", Context.MODE_PRIVATE)
            Log.d("TAG", "Saving: ${Gson().toJson(userOptions)}")
            fileOutputStream.write(Gson().toJson(userOptions).toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class WSClient {

        private var jwt: String? = null

        private val client = HttpClient {
            install(WebSockets)
        }

        private fun retrieveHistory() {
            val messageHistory = Klaxon().parseArray<String>(URL("https://chat.strims.gg/api/chat/history").readText())
            runOnUiThread {
                messageHistory?.forEach {
                    adapter.add(ChatMessage(parseMessage(it)!!))
                }
            }
        }

        private fun retrieveCookie() {
            val cookies = CookieManager.getInstance().getCookie("https://strims.gg")
            if (cookies != null) {
                Log.d("TAG", "Cookies: $cookies")
                val jwt = cookies.substringAfter("jwt=").substringBefore(" ")
                if (jwt != cookies) {
                    this.jwt = jwt
                }
                Log.d("TAG", "JWT: $jwt")
            }
        }

        @SuppressLint("SetTextI18n")
        private suspend fun retrieveProfile() {
            val text: String = client.get("https://strims.gg/api/profile") {
                header("Cookie", "jwt=$jwt")
            }
            Log.d("TAG", "Profile: $text")
            GlobalScope.launch {
                runOnUiThread {
                    CurrentUser.user = Klaxon().parse(text)
                    sendMessageText.hint = "Write something ${CurrentUser.user!!.username} ..."
                    invalidateOptionsMenu()
                }
            }
        }

        suspend fun onConnect() = client.wss(
            host = "chat.strims.gg",
            path = "/ws",
            request = {
                retrieveCookie()
                Log.d("TAG", "Requesting with JWT: $jwt")
                header("Cookie", "jwt=$jwt")
            }
        ){
            retrieveProfile()
            retrieveHistory()
            retrieveOptions()
            sendMessageButton.setOnClickListener {
                GlobalScope.launch {
                    val messageText = sendMessageText.text.toString()
                    val first = messageText.first()
                    if (first == '/') {
                        if (messageText.substringAfter(first).substringBefore(' ') == "w") {
                            val nick = messageText.substringAfter("/w ").substringBefore(' ')
                            send(
                                "PRIVMSG {\"nick\":\"$nick\", \"data\":\"${sendMessageText.text.toString().substringAfter("/w $nick ")}\"}"
                            )
                        } else if (messageText.substringAfter(first).substringBefore(' ') == "ignore") {
                            val nickIgnore = messageText.substringAfter("/ignore ").substringBefore(' ')
                            CurrentUser.options!!.ignoreList.add(nickIgnore)
                            saveOptions()
                        } else if (messageText.substringAfter(first).substringBefore(' ') == "unignore") {
                            val nickUnignore = messageText.substringAfter("/unignore ").substringBefore(' ')
                            if (CurrentUser.options!!.ignoreList.contains(nickUnignore)) {
                                CurrentUser.options!!.ignoreList.remove(nickUnignore)
                                saveOptions()
                                runOnUiThread {
                                    adapter.add(
                                        ChatMessage(
                                            Message(
                                                false,
                                                "Info",
                                                "Unignored: $nickUnignore",
                                                System.currentTimeMillis(),
                                                arrayOf()
                                            )
                                        )
                                    )
                                }
                            } else {
                                runOnUiThread {
                                    adapter.add(
                                        ChatMessage(
                                            Message(
                                                false,
                                                "Info",
                                                "User not currently ignored",
                                                System.currentTimeMillis(),
                                                arrayOf()
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        send("MSG {\"data\":\"${sendMessageText.text}\"}")
                    }
                    sendMessageText.text.clear()
                    runOnUiThread {
                        sendMessageButton.isEnabled = false
                    }
                }
            }
            while (true) {
                when (val frame = incoming.receive()) {
                    is Frame.Text -> {
                        println(frame.readText())
                        val msg: Message? = parseMessage(frame.readText())
                        if (msg != null) {
                            runOnUiThread {
                                if (msg.privMsg) {
                                    adapter.add(PrivateChatMessage(msg))
                                } else {
                                    adapter.add(ChatMessage(msg))
                                }
                                recyclerViewChat.scrollToPosition(adapter.itemCount - 1)
                            }
                        }
                    }
                    is Frame.Binary -> println(frame.readBytes())
                }
            }
        }

        private fun parseMessage(input: String): Message? {
            val msg = input.split(" ", limit = 2)
            val msgType = msg[0]
            if (msgType == "PRIVMSG") {
                val message = Klaxon().parse<Message>(msg[1])!!
                return Message(true, message.nick, message.data, message.timestamp, message.features)
            } else if (msgType == "MSG") {
                return Klaxon().parse<Message>(msg[1])
            }
            return null
        }
    }
}
