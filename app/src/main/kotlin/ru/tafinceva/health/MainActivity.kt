package ru.tafinceva.health

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tafinceva.health.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var huaweiRepository: HuaweiRepository
    private lateinit var googleDriveRepository: GoogleDriveRepository

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val huaweiAuthReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code  = intent.getStringExtra(AuthWebViewActivity.EXTRA_CODE)
            val error = intent.getStringExtra(AuthWebViewActivity.EXTRA_ERROR)
            when {
                code  != null -> onHuaweiCodeReceived(code)
                error != null -> showStatus("Ошибка Huawei: $error")
            }
        }
    }

    private val googleAuthReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code  = intent.getStringExtra(GoogleAuthActivity.EXTRA_CODE)
            val error = intent.getStringExtra(GoogleAuthActivity.EXTRA_ERROR)
            when {
                code  != null -> onGoogleCodeReceived(code)
                error != null -> showStatus("Ошибка Google: $error")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager         = TokenManager(this)
        huaweiRepository     = HuaweiRepository(tokenManager)
        googleDriveRepository = GoogleDriveRepository(tokenManager)

        requestNotificationPermission()
        setupUi()
        updateLastSyncLabel()

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)
            .observe(this) { infos ->
                val running = infos.any { it.state == WorkInfo.State.RUNNING }
                binding.btnSyncNow.isEnabled = !running
                if (running) showStatus("Синхронизация…")
            }
    }

    override fun onStart() {
        super.onStart()
        registerAuthReceiver(huaweiAuthReceiver, AuthWebViewActivity.ACTION_AUTH_CODE)
        registerAuthReceiver(googleAuthReceiver, GoogleAuthActivity.ACTION_GOOGLE_AUTH_CODE)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(huaweiAuthReceiver)
        unregisterReceiver(googleAuthReceiver)
    }

    private fun setupUi() {
        updateGoogleAuthStatus()

        // Load existing values
        binding.etHuaweiId.setText(tokenManager.huaweiAppId)
        binding.etGoogleId.setText(tokenManager.googleClientId)
        binding.etFolderId.setText(tokenManager.googleDriveFolderId)

        // Save on change
        binding.etHuaweiId.doAfterTextChanged { tokenManager.huaweiAppId = it?.toString()?.trim() }
        binding.etGoogleId.doAfterTextChanged { tokenManager.googleClientId = it?.toString()?.trim() }
        binding.etFolderId.doAfterTextChanged { tokenManager.googleDriveFolderId = it?.toString()?.trim() }

        binding.btnHuaweiLogin.setOnClickListener {
            if (tokenManager.huaweiAppId.isNullOrEmpty()) {
                Toast.makeText(this, "Введите Huawei App ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, AuthWebViewActivity::class.java))
        }

        binding.btnGoogleLogin.setOnClickListener {
            if (tokenManager.googleClientId.isNullOrEmpty()) {
                Toast.makeText(this, "Введите Google Client ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, GoogleAuthActivity::class.java))
        }

        binding.btnSyncNow.setOnClickListener {
            if (!tokenManager.isConfigured) {
                Toast.makeText(this, "Настройте ID и пройдите авторизацию", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            SyncScheduler.runNow(this)
            showStatus("Запущена синхронизация…")
        }
    }

    private fun onHuaweiCodeReceived(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        showStatus("Huawei: обмен кода…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { huaweiRepository.exchangeCode(code) }
                showStatus("Huawei ОК!")
                maybeScheduleSync()
            } catch (e: Exception) {
                showStatus("Ошибка Huawei: ${e.localizedMessage}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun onGoogleCodeReceived(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        showStatus("Google: обмен кода…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { googleDriveRepository.exchangeCode(code) }
                showStatus("Google ОК!")
                updateGoogleAuthStatus()
                maybeScheduleSync()
            } catch (e: Exception) {
                showStatus("Ошибка Google: ${e.localizedMessage}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun maybeScheduleSync() {
        if (tokenManager.isConfigured) SyncScheduler.schedule(this)
    }

    private fun updateGoogleAuthStatus() {
        val authorized = !tokenManager.googleRefreshToken.isNullOrEmpty()
        binding.btnGoogleLogin.text = if (authorized) "Google: авторизован ✓" else "Войти через Google"
    }

    private fun showStatus(message: String) {
        binding.tvStatus.text = message
    }

    private fun updateLastSyncLabel() {
        val last = tokenManager.lastSyncTime
        if (last == 0L) {
            binding.tvLastSync.text = "Последняя синхронизация: никогда"
        } else {
            val formatted = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(last))
            binding.tvLastSync.text = "Последняя синхронизация: $formatted"
        }
    }

    private fun registerAuthReceiver(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
