package com.callbox.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.callbox.app.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_EDIT = "force_edit"
    }

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val skipIfConfigured = intent.getBooleanExtra(EXTRA_FORCE_EDIT, false).not()
        if (skipIfConfigured && Prefs.hasConfig(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editKey.setText(Prefs.getPublishableKey(this))
        binding.editChannel.setText(Prefs.getChannel(this))

        binding.buttonSave.setOnClickListener {
            val key = binding.editKey.text?.toString()?.trim().orEmpty()
            val channel = binding.editChannel.text?.toString()?.trim().orEmpty()
            if (!key.startsWith("pk_")) {
                binding.textSetupError.text = getString(R.string.setup_error_key)
                return@setOnClickListener
            }
            if (channel.isEmpty()) {
                binding.textSetupError.text = getString(R.string.setup_error_channel)
                return@setOnClickListener
            }
            Prefs.save(this, key, channel)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
