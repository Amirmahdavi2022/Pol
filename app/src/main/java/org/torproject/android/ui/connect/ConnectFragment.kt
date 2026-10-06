package org.torproject.android.ui.connect

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.net.toUri
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.torproject.android.R
import org.torproject.android.databinding.FragmentConnectBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.vpn.VpnServicePrepareWrapper
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.ui.more.LogBottomSheet
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import org.torproject.android.util.sendIntentToService
import org.torproject.jni.TorService
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_THROTTLE_INTERVAL = 4000L

// Pol
private const val POL_CHANNEL_URL = "https://t.me/parsv2r"
private val POL_BRIDGE_TYPES = listOf("obfs4", "webtunnel", "snowflake", "meek_lite", "dnstt")

class ConnectFragment : Fragment(),
    ExitNodeBottomSheet.ExitNodeSelectedCallback {

    lateinit var binding: FragmentConnectBinding

    val viewModel: ConnectViewModel by activityViewModels()

    private val startTorVpnResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // The user pressed OK, we can start Tor VPN
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                startTorConnection()
            } else { /* this happens when:
                - the user cancels the system VPN dialog
                - the user is on Android S+ and has another VpnService based app
                  set to be always-on.

                  we are unable to differentiate these two things, so show a generic error msg
                 */
                displayVpnStartError(getString(R.string.unable_to_start_unknown_reason_error_msg))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Pol: the switch only carries the connect logic, the round button is the UI.
                    binding.switchConnect.visibility = View.GONE
                    // Pol: remember a start that never reached "connected", to tell the user
                    // when every way failed instead of silently going back to the off screen.
                    if (state is ConnectUiState.Starting) polWasConnecting = true
                    if (state is ConnectUiState.On) polWasConnecting = false
                    when (state) {
                        is ConnectUiState.NoInternet -> doLayoutNoInternet()
                        is ConnectUiState.Off -> {
                            turnOffConnectSwitchWithoutEvent()
                            doLayoutOff()
                            if (polWasConnecting && !polUserStopped) showPolFailedDialog()
                            polWasConnecting = false
                            polUserStopped = false
                        }
                        is ConnectUiState.Starting -> {
                            binding.switchConnect.isChecked = true
                            state.bootstrapPercent?.let {
                                viewModel.updateBootstrapPercent(it)
                            }
                            doLayoutStarting(requireContext())
                        }

                        is ConnectUiState.On -> {
                            binding.switchConnect.isChecked = true
                            lastState = TorService.ACTION_START
                            doLayoutOn()
                        }

                        is ConnectUiState.Stopping -> {}
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logState.collect { logline ->
                    LocalizedLogsToDisplay.updateLabelIfDisplayed(logline, context)?.let {
                        viewModel.updateSubtitleState(it)
                        // Pol: while connecting the subtitle shows which way is being tried instead.
                        if (viewModel.uiState.value !is ConnectUiState.Starting) {
                            binding.tvSubtitle.text = it
                        }
                    }
                }
            }
        }

        binding.switchConnect.setOnClickListener {
            it.isEnabled = false
            it.alpha = 0.38f
            binding.switchConnect.text = context?.getString(R.string.loading)
            it.postDelayed({
                it.isEnabled = true
                it.alpha = 1f
                binding.switchConnect.text = context?.getString(R.string.connect)
            }, DEFAULT_THROTTLE_INTERVAL)
        }
        binding.switchConnect.setOnCheckedChangeListener(onCheckChanged)

        // Pol: one big round button. Throttled like the original switch.
        binding.ivStatus.setOnClickListener { orb ->
            if (viewModel.uiState.value is ConnectUiState.NoInternet) return@setOnClickListener
            orb.isEnabled = false
            orb.postDelayed({ orb.isEnabled = true }, DEFAULT_THROTTLE_INTERVAL)
            val turnOn = !binding.switchConnect.isChecked
            if (!turnOn) polUserStopped = true
            binding.switchConnect.isChecked = turnOn
        }
        binding.tvSubtitle.setOnClickListener {
            LogBottomSheet.show(parentFragmentManager)
        }
        // Pol: the button shrinks a little under the finger so the tap feels physical.
        binding.ivStatus.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }

        refreshMenuList(requireContext())
    }

    private var polWasConnecting = false
    private var polUserStopped = false

    private fun showPolFailedDialog() {
        val ctx = context ?: return
        if (!isAdded) return
        AlertDialog.Builder(ctx, R.style.OrbotDialogTheme)
            .setTitle(R.string.pol_failed_title)
            .setMessage(R.string.pol_failed_message)
            .setPositiveButton(R.string.pol_get_bridges) { _, _ -> openBridgeBot() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openBridgeBot() {
        Toast.makeText(requireContext(), R.string.pol_get_bridges_hint, Toast.LENGTH_LONG).show()
        openUrl(OrbotConstants.GET_BRIDES_TELEGRAM_BOT.toString())
    }

    private fun setOrb(background: Int, label: Int) {
        binding.ivStatus.setBackgroundResource(background)
        binding.tvOrbLabel.setText(label)
    }

    private fun polMethodName(transport: Transport): String = getString(
        when (transport) {
            Transport.NONE -> R.string.pol_method_direct
            Transport.SNOWFLAKE, Transport.SNOWFLAKE_AMP, Transport.SNOWFLAKE_SQS -> R.string.pol_method_snowflake
            Transport.CUSTOM -> R.string.pol_method_custom
            Transport.WEBTUNNEL -> R.string.pol_webtunnel
            Transport.OBFS4 -> R.string.pol_method_obfs4
            Transport.MEEK -> R.string.pol_method_meek
            Transport.DNSTT -> R.string.pol_method_dnstt
        }
    )

    private val onCheckChanged: CompoundButton.OnCheckedChangeListener = { _, value ->
        if (value) {
            // display msg if optional outbound proxy config is invalid
            if (Prefs.outboundProxy.second != null) {
                Toast.makeText(
                    activity,
                    getString(R.string.invalid_outbound_proxy_config),
                    Toast.LENGTH_LONG
                ).show()
            }
            attemptToStartTor()
        } else {
            stopTorAndVpn()
        }
    }

    private fun turnOffConnectSwitchWithoutEvent() {
        binding.switchConnect.setOnCheckedChangeListener(null)
        binding.switchConnect.isChecked = false
        binding.switchConnect.setOnCheckedChangeListener(onCheckChanged)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onResume() {
        super.onResume()
        updatePrivateDnsLabel()
        view?.viewTreeObserver?.addOnWindowFocusChangeListener(clipboardFocusListener)
        // Already focused when coming back from another app? Then check right away.
        if (activity?.hasWindowFocus() == true) checkClipboardForBridges()
    }

    override fun onPause() {
        view?.viewTreeObserver?.removeOnWindowFocusChangeListener(clipboardFocusListener)
        super.onPause()
    }

    // Pol: Android only lets an app read the clipboard while its window has focus,
    // so the check runs on focus instead of in onResume alone.
    private val clipboardFocusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) checkClipboardForBridges()
        }

    private var lastOfferedClip: String? = null

    /**
     * Pol: the user copied bridge lines (e.g. from the Tor bridges Telegram bot) and came back.
     * Offer to use them, so nobody has to find the custom bridges screen and paste by hand.
     */
    private fun checkClipboardForBridges() {
        val ctx = context ?: return
        if (!isAdded) return

        val text = try {
            val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount < 1) return
            clip.getItemAt(0).coerceToText(ctx)?.toString()
        } catch (_: Exception) {
            null
        } ?: return

        if (text == lastOfferedClip) return

        val bridges = CustomBridgeBottomSheet.cleanBridgeLines(text)
            .map { it.trim() }
            .filter { line ->
                POL_BRIDGE_TYPES.any { line.startsWith("$it ") } &&
                        CustomBridgeBottomSheet.isValidBridge(line)
            }

        if (bridges.isEmpty() || bridges == Prefs.bridgesList) return

        lastOfferedClip = text

        AlertDialog.Builder(ctx, R.style.OrbotDialogTheme)
            .setTitle(R.string.pol_clip_title)
            .setMessage(getString(R.string.pol_clip_message, bridges.size))
            .setPositiveButton(R.string.pol_clip_use) { _, _ ->
                Prefs.bridgesList = bridges
                Prefs.smartConnect = true
                Prefs.lastGoodTransport = Transport.CUSTOM
                refreshMenuList(ctx)

                if (viewModel.uiState.value is ConnectUiState.Off) {
                    // Triggers onCheckChanged, which starts the connection.
                    binding.switchConnect.isChecked = true
                } else {
                    Toast.makeText(ctx, R.string.pol_clip_saved_reconnect, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show()
        }
    }

    // see https://github.com/guardianproject/orbot-android/pull/1707
    @SuppressLint("SetTextI18n")
    private fun updatePrivateDnsLabel() {
        val privateDns = NetworkUtils.PrivateDns.getPrivateDnsConfiguration(requireContext())
        when (privateDns) {
            is NetworkUtils.PrivateDns.Off, NetworkUtils.PrivateDns.Opportunistic -> {
                binding.tvPrivateDnsStatus.visibility = View.GONE
            }

            is NetworkUtils.PrivateDns.Strict -> {
                binding.tvPrivateDnsStatus.apply {
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    visibility = View.VISIBLE
                    text = "${getString(R.string.private_dns_strict)}\n${privateDns.hostname}"
                    setOnClickListener {
                        AlertDialog.Builder(requireContext(), R.style.OrbotDialogTheme)
                            .setTitle(R.string.private_dns)
                            .setIcon(R.drawable.ic_stat_notifyerr)
                            .setMessage(R.string.private_dns_explanation)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    fun stopTorAndVpn() {
        polUserStopped = true
        doLayoutOff()
        setState(TorService.ACTION_STOP)
        binding.tvSubtitle.text = ""
    }

    private fun stopAnimations() {
        binding.ivStatus.clearAnimation()
        binding.ivShadow.clearAnimation()
    }

    private fun sendNewnymSignal() {
        requireContext().sendIntentToService(OrbotService.SIGNAL_NEWNYM)
        binding.ivStatus.animate().alpha(0f).duration = 500

        lifecycleScope.launch(Dispatchers.Main) {
            delay(600.milliseconds)
            binding.ivStatus.animate().alpha(1f).duration = 500
        }
    }

    private fun attemptToStartTorPowerUserMode(): Boolean {
        // android 14 awkwardly needs this permission to be explicitly granted to use the
        // FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED permission without grabbing a VPN Intent
        val alarmManager =
            requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            PowerUserForegroundPermDialog().createTransactionAndShow(requireActivity())
            turnOffConnectSwitchWithoutEvent()
            return false // user can try again after granting permission
        }
        if (PowerUserBatteryOptimizations.shouldShowDialog(requireActivity())) {
            PowerUserBatteryOptimizations().show(
                requireActivity().supportFragmentManager,
                PowerUserBatteryOptimizations.TAG
            )
        }
        startTorConnection()
        return true
    }


    // starts Tor, plus often the VPNService. For power users it runs tor as a proxy
    private fun startTorConnection() = setState(TorService.ACTION_START)

    fun attemptToStartTor() {
        // Pol has no manual transport picker, so every connect is an automatic one.
        Prefs.smartConnect = true
        polUserStopped = false
        Prefs.putUseVpn(!Prefs.isPowerUserMode)
        if (Prefs.isPowerUserMode) {
            // if the user hasn't met all the conditions of power user mode
            if (!attemptToStartTorPowerUserMode()) {
                binding.switchConnect.isChecked = false
            }
        } else {
            val vpnPrepareState =
                VpnServicePrepareWrapper.orbotVpnServicePreparedState(requireContext())

            when (vpnPrepareState) {

                is VpnServicePrepareWrapper.Result.Prepared ->
                    startTorConnection()

                is VpnServicePrepareWrapper.Result.CantPrepare ->
                    displayVpnStartError(vpnPrepareState.errorMsg)

                is VpnServicePrepareWrapper.Result.ShouldAttempt ->
                    // prompt VPN permission dialog
                    startTorVpnResultLauncher.launch(vpnPrepareState.prepareIntent)

            }
        }
        refreshMenuList(requireContext())
    }

    private fun displayVpnStartError(msg: String) {
        turnOffConnectSwitchWithoutEvent()
        VpnAlwaysOnDialog.newInstance(msg).show(
            requireActivity().supportFragmentManager,
            VpnAlwaysOnDialog.TAG
        )
    }

    private var lastState: String? = null

    @Synchronized
    fun setState(newState: String) {
        if (lastState != newState) {
            requireContext().sendIntentToService(newState)
            lastState = newState
        }
    }

    fun refreshMenuList(context: Context) {

        // Pol: no transport / exit / app pickers on the main screen, only these two.
        val listItems = arrayListOf(
            OrbotMenuAction(R.string.pol_get_bridges, R.drawable.ic_vpn_key) { openBridgeBot() },
            OrbotMenuAction(R.string.pol_channel, R.drawable.ic_heart) { openUrl(POL_CHANNEL_URL) },
        )
        binding.lvConnected.adapter = ConnectMenuActionAdapter(context, listItems)
    }


    private fun doLayoutNoInternet() {
        stopAnimations()
        setOrb(R.drawable.pol_orb_off, R.string.pol_orb_connect)
        binding.ivStatus.alpha = 0.5f
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = getString(R.string.pol_no_internet_title)
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.tvSubtitle.text = getString(R.string.pol_no_internet_subtitle)
        binding.lvConnected.visibility = View.VISIBLE
    }

    // this context param is because we call this method from a screenshot taking test script
    fun doLayoutOn(context: Context = requireContext()) {
        // Pol: automatic mode stays on after connecting; SmartConnect remembers what worked.
        stopAnimations()
        setOrb(R.drawable.pol_orb_on, R.string.pol_orb_disconnect)
        binding.ivStatus.alpha = 1f
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = context.getString(R.string.pol_title_on)
        binding.tvSubtitle.text = context.getString(R.string.pol_subtitle_on)
        binding.lvConnected.visibility = View.VISIBLE
        refreshMenuList(context)
    }

    private fun doLayoutOff() {
        viewModel.updateSubtitleState()
        stopAnimations()
        setOrb(R.drawable.pol_orb_off, R.string.pol_orb_connect)
        binding.ivStatus.alpha = 1f
        refreshMenuList(requireContext())
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.lvConnected.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.pol_title_off)
        binding.tvSubtitle.text = getString(R.string.pol_subtitle_off)
    }

    private fun doLayoutStarting(context: Context) {
        val percent = (viewModel.uiState.value as? ConnectUiState.Starting)?.bootstrapPercent ?: 0

        setOrb(R.drawable.pol_orb_starting, R.string.pol_orb_connecting)
        binding.ivStatus.alpha = 1f
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.tvSubtitle.text = if (percent <= 0) {
            context.getString(R.string.pol_status_preparing)
        } else {
            context.getString(R.string.pol_status_trying, polMethodName(Prefs.transport), percent)
        }
        with(binding.progressBar) {
            visibility = View.VISIBLE
            progress = percent
        }

        if (binding.ivStatus.animation == null) {
            val pulse = AnimationUtils.loadAnimation(context, R.anim.pol_pulse)
            binding.ivStatus.startAnimation(pulse)
        }
        binding.tvTitle.text = context.getString(R.string.pol_title_starting)
    }


    private fun openConfigureTorConnection() {
        ConfigConnectionBottomSheet()
            .show(requireActivity().supportFragmentManager, ConfigConnectionBottomSheet.TAG)
    }

    override fun onExitNodeSelected(countryCode: String) {

        //tor format expects "{" for country code
        Prefs.exitNodes = "{$countryCode}"

        requireContext().sendIntentToService(
            Intent(requireActivity(), OrbotService::class.java)
                .setAction(OrbotConstants.CMD_SET_EXIT).putExtra("exit", countryCode)
        )

        refreshMenuList(requireContext())
    }
}
