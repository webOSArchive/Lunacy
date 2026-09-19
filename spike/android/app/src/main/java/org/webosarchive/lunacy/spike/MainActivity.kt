package org.webosarchive.lunacy.spike

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView

/** Spike launcher: lists bundled apps and Enyo samples, opens one in a card. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = Catalog.entries(assets)
        val bridge = CheckBox(this).apply { text = "Inject PalmSystem bridge"; isChecked = true }
        val list = ListView(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, entries.map { it.first })
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@MainActivity, CardActivity::class.java)
                    .putExtra("url", entries[pos].second)
                    .putExtra("bridge", bridge.isChecked))
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bridge)
            addView(list)
        })
    }
}

object Catalog {
    const val FW = "usr/palm/frameworks/enyo/1.0"

    fun entries(am: android.content.res.AssetManager): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (id in am.list("apps").orEmpty().sorted()) {
            out += "App: $id" to Paths.appUrl(id)
        }
        val ex = "$FW/support/examples"
        fun walk(rel: String) {
            val kids = am.list("fw/enyo/1.0/support/examples/$rel").orEmpty()
            if ("index.html" in kids) {
                out += "Sample: $rel" to "https://com.palmdts.enyo.samples.media.cryptofs.apps/$ex/$rel/index.html"
            } else for (k in kids.sorted()) {
                if (!k.contains('.')) walk(if (rel.isEmpty()) k else "$rel/$k")
            }
        }
        walk("")
        return out
    }
}
