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

    // ── Notification permission (Android 13+) ─────────────────

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    // ── BroadcastReceiver for Huawei OAuth code ───────────────

    private val authReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code  = intent.getStringExtra(AuthWebViewActivity.EXTRA_CODE)
            val error = intent.getStringExtra(AuthWebViewActivity.EXTRA_ERROR)

            when {
                code != null  -> onHuaweiCodeReceived(code)
                error != null -> showStatus("Ошибка авторизации: $error")
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager     = TokenManager(this)
        huaweiRepository = HuaweiRepository(tokenManager)

        requestNotificationPermission()
        setupUi()
        updateLastSyncLabel()

        // Observe background sync work state
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
        val filter = IntentFilter(AuthWebViewActivity.ACTION_AUTH_CODE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(authReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(authReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(authReceiver)
    }

    // ── UI setup ──────────────────────────────────────────────

    private fun setupUi() {
        // Pre-fill Google refresh token if already stored
        binding.etGoogleToken.setText(tokenManager.googleRefreshToken ?: "")

        binding.btnHuaweiLogin.setOnClickListener {
            startActivity(Intent(this, AuthWebViewActivity::class.java))
        }

        binding.btnSaveGoogleToken.setOnClickListener {
            val token = binding.etGoogleToken.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "Введите Google refresh token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tokenManager.googleRefreshToken = token
            SyncScheduler.schedule(this)
            showStatus("Google token сохранён. Фоновая синхронизация активирована.")
        }

        binding.btnSyncNow.setOnClickListener {
            val googleToken = binding.etGoogleToken.text.toString().trim()
            if (googleToken.isNotEmpty()) {
                tokenManager.googleRefreshToken = googleToken
            }

            if (!tokenManager.isConfigured) {
                Toast.makeText(
                    this,
                    "Сначала войдите через Huawei и сохраните Google token",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            SyncScheduler.runNow(this)
            showStatus("Запущена синхронизация…")
        }
    }

    // ── Huawei OAuth callback ─────────────────────────────────

    private fun onHuaweiCodeReceived(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        showStatus("Получен код авторизации, обмен на токен…")

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    huaweiRepository.exchangeCode(code)
                }
                showStatus("Huawei авторизация успешна!")
                binding.btnSyncNow.isEnabled = true
                if (tokenManager.isConfigured) {
                    SyncScheduler.schedule(this@MainActivity)
                }
            } catch (e: Exception) {
                showStatus("Ошибка обмена токена: ${e.localizedMessage}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun showStatus(message: String) {
        binding.tvStatus.text = message
    }

    private fun updateLastSyncLabel() {
        val last = tokenManager.lastSyncTime
        if (last == 0L) {
            binding.tvLastSync.text = "Последняя синхронизация: никогда"
        } else {
            val formatted = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(last))
            binding.tvLastSync.text = "Последняя синхронизация: $formatted"
        }

        // Refresh label whenever WorkManager finishes
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)
            .observe(this) { infos ->
                val anySucceeded = infos.any { it.state == WorkInfo.State.SUCCEEDED }
                if (anySucceeded) updateLastSyncLabel()
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
