/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import mozilla.components.support.ktx.android.view.showKeyboard
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.R.string.pref_download_file
import org.mozilla.reference.browser.R.string.pref_key_about_page
import org.mozilla.reference.browser.R.string.pref_key_firefox_account
import org.mozilla.reference.browser.R.string.pref_key_make_default_browser
import org.mozilla.reference.browser.R.string.pref_key_override_amo_collection
import org.mozilla.reference.browser.R.string.pref_key_pair_sign_in
import org.mozilla.reference.browser.R.string.pref_key_privacy
import org.mozilla.reference.browser.R.string.pref_key_remote_debugging
import org.mozilla.reference.browser.R.string.pref_key_sign_in
import org.mozilla.reference.browser.autofill.AutofillPreference
import org.mozilla.reference.browser.ext.getPreferenceKey
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.sync.BrowserFxAEntryPoint
import java.io.File
import kotlin.system.exitProcess

private typealias RBSettings = org.mozilla.reference.browser.settings.Settings

@Suppress("TooManyFunctions")
class SettingsFragment : PreferenceFragmentCompat() {

    interface ActionBarUpdater {
        fun updateTitle(titleResId: Int)
    }

    private val defaultClickListener = OnPreferenceClickListener { preference ->
        Toast.makeText(context, "${preference.title} Clicked", LENGTH_SHORT).show()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }



    override fun onResume() {
        super.onResume()

        setupPreferences()
        getActionBarUpdater().apply {
            updateTitle(R.string.settings)
        }
    }

    @Suppress("LongMethod") // Yep, this should be refactored.
    private fun setupPreferences() {
        val signInKey = requireContext().getPreferenceKey(pref_key_sign_in)
        val signInPairKey = requireContext().getPreferenceKey(pref_key_pair_sign_in)
        val firefoxAccountKey = requireContext().getPreferenceKey(pref_key_firefox_account)
        val downloadFileKey = requireContext().getPreferenceKey(pref_download_file)
        val makeDefaultBrowserKey = requireContext().getPreferenceKey(pref_key_make_default_browser)
        val remoteDebuggingKey = requireContext().getPreferenceKey(pref_key_remote_debugging)
        val aboutPageKey = requireContext().getPreferenceKey(pref_key_about_page)
        val privacyKey = requireContext().getPreferenceKey(pref_key_privacy)
        val customAddonsKey = requireContext().getPreferenceKey(pref_key_override_amo_collection)
        val autofillPreferenceKey = requireContext().getPreferenceKey(R.string.pref_key_autofill)

        val preferenceSignIn = findPreference<Preference>(signInKey)
        val preferencePairSignIn = findPreference<Preference>(signInPairKey)
        val downloadFile = findPreference<Preference>(downloadFileKey)
        val preferenceFirefoxAccount = findPreference<Preference>(firefoxAccountKey)
        val preferenceMakeDefaultBrowser = findPreference<Preference>(makeDefaultBrowserKey)
        val preferenceRemoteDebugging = findPreference<SwitchPreferenceCompat>(remoteDebuggingKey)
        val preferenceAboutPage = findPreference<Preference>(aboutPageKey)
        val preferencePrivacy = findPreference<Preference>(privacyKey)
        val preferenceCustomAddons = findPreference<Preference>(customAddonsKey)
        val preferenceAutofill = findPreference<AutofillPreference>(autofillPreferenceKey)

        val accountManager = requireComponents.backgroundServices.accountManager
        if (accountManager.authenticatedAccount() != null) {
            preferenceSignIn?.isVisible = false
            preferencePairSignIn?.isVisible = false
            preferenceFirefoxAccount?.summary = accountManager.accountProfile()?.email.orEmpty()
            preferenceFirefoxAccount?.onPreferenceClickListener = getClickListenerForFirefoxAccount()
        } else {
            preferenceSignIn?.isVisible = true
            preferenceFirefoxAccount?.isVisible = false
            preferenceFirefoxAccount?.onPreferenceClickListener = null
            preferenceSignIn?.onPreferenceClickListener = getClickListenerForSignIn()
            preferencePairSignIn?.isVisible = true
            preferencePairSignIn?.onPreferenceClickListener = getClickListenerForPairingSignIn()
        }

        if (!AutofillPreference.isSupported(requireContext())) {
            preferenceAutofill?.isVisible = false
        } else {
            (preferenceAutofill as AutofillPreference).updateSwitch()
        }

        preferenceMakeDefaultBrowser?.onPreferenceClickListener = getClickListenerForMakeDefaultBrowser()
        preferenceRemoteDebugging?.onPreferenceChangeListener = getChangeListenerForRemoteDebugging()
        preferenceAboutPage?.onPreferenceClickListener = getAboutPageListener()
        preferencePrivacy?.onPreferenceClickListener = getClickListenerForPrivacy()
        preferenceCustomAddons?.onPreferenceClickListener = getClickListenerForCustomAddons()

        downloadFile?.onPreferenceClickListener = getClickListenerForDownloadFile()
    }

    private fun getClickListenerForMakeDefaultBrowser(): OnPreferenceClickListener {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            OnPreferenceClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                )
                startActivity(intent)
                true
            }
        } else {
            defaultClickListener
        }
    }

    private fun getClickListenerForSignIn(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            requireComponents.services.accountsAuthFeature.beginAuthentication(
                requireContext(),
                BrowserFxAEntryPoint.HomeMenu,
            )
            activity?.finish()
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode){
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showFileDownloadDialog();
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Permission Denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun isStoragePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        );
    }


    private fun getClickListenerForDownloadFile(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            showFileDownloadDialog();

            true
        }
    }


// Function to prompt to select from a list of files available to download
    private fun showFileDownloadDialog() {
        val context = requireContext()
        val items = arrayOf("README.md", "full_description.txt")
        AlertDialog.Builder(context).apply {
            setTitle("Choose File to Download")
            setItems(items) { _, index ->
                val fileName = items[index]
                val url = when (index) {
                    0 -> "https://gitlab.com/censorship-no/ceno-browser/-/raw/main/README.md"
                    1 -> "https://gitlab.com/censorship-no/ceno-browser/-/raw/main/fastlane/metadata/android/en-US/full_description.txt"
                    else -> ""
                }
                if (url.isNotEmpty()) {
                    promptFileNameAndDownload(url, fileName)
                }
            }
        }.show()
    }

    // Prompt to Enter FileName and initial download
    private fun promptFileNameAndDownload(url: String, defaultFileName: String){
        val editText = EditText(requireContext())
        editText.setText(defaultFileName)

        AlertDialog.Builder(requireContext()).apply {
            setTitle("Enter File Name")
            setView(editText)
            setPositiveButton("Download") { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    downloadFile(url, fileName)
                } else {
                    Toast.makeText(requireContext(), "Invalid file name", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Cancel", null)
        }.show()
    }

    // Function to donwload the File selected using the Android DownloadManager, also listening if the download is completed using the Broadcast Receiver
    private fun downloadFile(url: String, fileName: String){
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(requireContext(), "Downloading $fileName", Toast.LENGTH_SHORT).show()

        val onCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()){
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (statusIndex != -1 && localUriIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val fileUri = Uri.parse(cursor.getString(localUriIndex))
                            val file = File(fileUri.path!!)
                            showDownloadCompletedDialog(file)
                        }
                    }
                }
                cursor.close()
            }
        }

       requireContext().registerReceiver(onCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

    }




    // Function to open File In a supported application considering the MIME type
    private fun openFileInBrowser(file: File){

        val contentResolver = context?.contentResolver


        val intent = Intent(Intent.ACTION_VIEW)
        val fileUri = FileProvider.getUriForFile(requireContext(), "org.mozilla.reference.browser.debug.fileprovider", file)
        intent.setDataAndType(fileUri, contentResolver?.getType(fileUri))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooserIntent = Intent.createChooser(intent, "Open File")
        chooserIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(contentResolver?.getType(fileUri)))

        try {
            startActivity(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                "No application found to open the file",
                Toast.LENGTH_SHORT
            ).show()
        }
 }





    // Function to prompt the downloaded file is completed
    private fun showDownloadCompletedDialog(file: File){
        val fileName = file.name

        val dialogBuilder = AlertDialog.Builder(requireContext())
        dialogBuilder.setTitle("Download Completed")
            .setMessage("File $fileName has been downloaded successfully. ")
            .setPositiveButton("View in Browser") { _, _ ->


                val isBrowserInstalled = isMozillaBrowserInstalled()

                if (isBrowserInstalled) {
                    Toast.makeText(requireContext(), "Yes Browser is installed", Toast.LENGTH_SHORT).show()
                    openFileInBrowser(file)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Mozilla Android Reference Browser not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
            .apply {
                setOnDismissListener {

                    val isBrowserInstalled = isMozillaBrowserInstalled()

                    if (!isBrowserInstalled){
                        openFileInBrowser(file)
                    }
                }
            }
        dialogBuilder.setCancelable(false)
    }


    // Checking if Mozilla Reference Browser is installed
    private fun isMozillaBrowserInstalled(): Boolean {
        val packageName = "org.mozilla.reference.browser.debug"
        val packageManager = requireContext().packageManager

        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException){
            false
        }
    }

    private fun getClickListenerForPairingSignIn(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, PairSettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.pair_preferences)
            }
            true
        }
    }

    private fun getClickListenerForFirefoxAccount(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, AccountSettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.account_settings)
            }
            true
        }
    }

    private fun getClickListenerForPrivacy(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, PrivacySettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.privacy_settings)
            }
            true
        }
    }

    private fun getChangeListenerForRemoteDebugging(): OnPreferenceChangeListener {
        return OnPreferenceChangeListener { _, newValue ->
            requireComponents.core.engine.settings.remoteDebuggingEnabled = newValue as Boolean
            true
        }
    }

    private fun getAboutPageListener(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, AboutFragment())
                .addToBackStack(null)
                .commit()
            true
        }
    }

    private fun getActionBarUpdater() = activity as ActionBarUpdater

    private fun getClickListenerForCustomAddons(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            val context = requireContext()
            val dialogView = View.inflate(context, R.layout.amo_collection_override_dialog, null)
            val userView = dialogView.findViewById<EditText>(R.id.custom_amo_user)
            val collectionView = dialogView.findViewById<EditText>(R.id.custom_amo_collection)

            AlertDialog.Builder(context).apply {
                setTitle(context.getString(R.string.preferences_customize_amo_collection))
                setView(dialogView)
                setNegativeButton(R.string.customize_addon_collection_cancel) { dialog: DialogInterface, _ ->
                    dialog.cancel()
                }

                setPositiveButton(R.string.customize_addon_collection_ok) { _, _ ->
                    RBSettings.setOverrideAmoUser(context, userView.text.toString())
                    RBSettings.setOverrideAmoCollection(context, collectionView.text.toString())

                    Toast.makeText(
                        context,
                        getString(R.string.toast_customize_addon_collection_done),
                        Toast.LENGTH_LONG,
                    ).show()

                    Handler().postDelayed(
                        {
                            exitProcess(0)
                        },
                        AMO_COLLECTION_OVERRIDE_EXIT_DELAY,
                    )
                }

                collectionView.setText(RBSettings.getOverrideAmoCollection(context))
                userView.setText(RBSettings.getOverrideAmoUser(context))
                userView.requestFocus()
                userView.showKeyboard()
                create()
            }.show()
            true
        }
    }
    companion object {
        private const val AMO_COLLECTION_OVERRIDE_EXIT_DELAY = 3000L
        private const val STORAGE_PERMISSION_REQUEST_CODE = 100
    }
}
