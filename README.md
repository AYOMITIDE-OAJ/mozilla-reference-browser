#  Mozilla Reference Browser Modified 

### Solution Steps and Reasoning

**Step 1:** Open settings page To open the settings page, the code should
            navigate to the settings fragment. This can be achieved by using the
            PreferenceFragmentCompat and setPreferencesFromResource methods.

**Step 2:** Select \"Download Cool File\" from menu options In the
            preferences XML file (preferences.xml), a preference item with the key
            pref_download_file is defined. This preference item represents the
            \"Download Cool File\" option in the settings menu.

**Step 3:** Prompt to choose from a list of files When the \"Download Cool
            File\" option is selected, a dialog should be shown to allow the user to
            choose from a list of files. The list of files is defined in the
            showFileDownloadDialog function. The dialog displays the file names as
            options and assigns the corresponding URLs to each option.

**Step 4:** Prompt to enter a name for the file After selecting a file from
            the list, the code prompts the user to enter a name for the file. This
            is done using an AlertDialog with an EditText view to input the file
            name. The default file name is provided as a suggestion.

**Step 5:** Prompt to confirm the download Once the user enters a file name
            and clicks the \"Download\" button, the code initiates the file download
            process using the Android Download Manager. A toast message is displayed
            to indicate that the file is being downloaded.
            
**Step 6:** After download completes, prompt to view the file in the browser When the download completes, a broadcast receiver is registered to
            listen for the completion event. Once the download is successful, a  dialog is shown to inform the user that the download is completed. The
            dialog provides options to either view the file in the browser or close
            the dialog.
            
**Step 7:** Open the file in the browser: If the user chose to view the file
            in the browser, the file was opened using a file provider and the
            appropriate MIME type. If the Mozilla Android Reference Browser was
            installed, the file was opened in that browser. Otherwise, an
            alternative application capable of handling the file was used.
            
**Step 8:** Handle scenarios without the browser: In case the Mozilla
            Android Reference Browser was not installed, the user was prompted to
            open the file using any available application capable of handling the
            file.


# Download Nightly Builds Directly

Apk build available for download from:

* [⬇️ ARM64/Aarch64 devices (64 bit; Android 5+)](https://)



