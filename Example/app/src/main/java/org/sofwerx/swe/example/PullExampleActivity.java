package org.sofwerx.swe.example;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;
import org.sofwerx.ogc.sos.AbstractSosOperation;
import org.sofwerx.ogc.sos.HttpHelper;
import org.sofwerx.ogc.sos.OperationGetCapabilities;
import org.sofwerx.ogc.sos.OperationGetCapabilitiesResponse;
import org.sofwerx.ogc.sos.SensorMeasurement;
import org.sofwerx.ogc.sos.SensorMeasurementTime;
import org.sofwerx.ogc.sos.SensorResultTemplateField;
import org.sofwerx.ogc.sos.SosIpcTransceiver;
import org.sofwerx.ogc.sos.SosSensor;
import org.sofwerx.ogc.sos.SosService;
import org.sofwerx.ogc.sos.SosMessageListener;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Random;

public class PullExampleActivity extends AppCompatActivity implements SosMessageListener {
    private SosService sosService;
    private ArrayList<SosSensor> sensors;

    @Override
    public void onDestroy() {
        if (sosService != null)
            sosService.shutdown(); //for proper memory management, remember to shutdown the SOS service so it can do its cleanup as well
        saveValuesToPreferences();
        super.onDestroy();
    }
    /**
     * Below is all the GUI and support coded needed to display and alter the sensor info
     */

    private TextInputEditText editSosServerUrl, editSosUsername, editSosPassword;
    private CheckBox checkSendIpc, checkSendNet;
    private TextView textCannotSendWarning, textResult;
    private Button sendButton;
    private View viewMeasurements;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pull);
        editSosServerUrl = findViewById(R.id.editUrl);
        editSosUsername = findViewById(R.id.editUsername);
        editSosPassword = findViewById(R.id.editPassword);
        checkSendIpc = findViewById(R.id.checkSendIpc);
        checkSendNet = findViewById(R.id.checkSendNet);
        textCannotSendWarning = findViewById(R.id.textSendWarning);
        textResult = findViewById(R.id.result);
        sendButton = findViewById(R.id.send);
        viewMeasurements = findViewById(R.id.viewMeasurements);
        sendButton.setOnClickListener(v -> sendQuery());
        checkSendIpc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVisibility();
            if (sosService != null)
                sosService.setIpcBroadcast(isChecked);
        });
        checkSendNet.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVisibility();
            if (sosService != null)
                sosService.setSosServerUrl(isChecked?editSosServerUrl.getText().toString():null);
        });
        textResult.setMovementMethod(new ScrollingMovementMethod());
        loadValuesFromPreferences();
    }

    private void sendQuery() {
        if (sosService == null) {
            String url;
            if (checkSendNet.isChecked())
                url = editSosServerUrl.getText().toString();
            else
                url = null;
            String username = editSosUsername.getText().toString();
            String sosPassword = editSosPassword.getText().toString();
            log("Querying server...");
            sosService = new SosService(PullExampleActivity.this, null, url, username, sosPassword, true, checkSendIpc.isChecked());
        }
        sosService.broadcast(new OperationGetCapabilities());
    }

    private final static String PREFS_SEND_IPC = "ipc";
    private final static String PREFS_SEND_NET = "net";
    private final static String PREFS_SOS_URL = "url";
    private final static String PREFS_SOS_USERNAME = "usr";
    private final static String PREFS_SOS_PASSWORD = "pwd";

    private void loadValuesFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editSosServerUrl.setText(prefs.getString(PREFS_SOS_URL,null));
        editSosUsername.setText(prefs.getString(PREFS_SOS_USERNAME,null));
        editSosPassword.setText(prefs.getString(PREFS_SOS_PASSWORD,null));
        checkSendIpc.setChecked(prefs.getBoolean(PREFS_SEND_IPC,true));
        checkSendNet.setChecked(prefs.getBoolean(PREFS_SEND_NET,false));
    }

    private void saveValuesToPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();
        String url = editSosServerUrl.getText().toString();
        if ((url != null) && (url.length() > 0))
            edit.putString(PREFS_SOS_URL,url);
        else
            edit.remove(PREFS_SOS_URL);
        String usr = editSosUsername.getText().toString();
        if ((usr != null) && (usr.length() > 0))
            edit.putString(PREFS_SOS_USERNAME,usr);
        else
            edit.remove(PREFS_SOS_USERNAME);
        String pwd = editSosPassword.getText().toString();
        if ((pwd != null) && (pwd.length() > 0))
            edit.putString(PREFS_SOS_PASSWORD,pwd);
        else
            edit.remove(PREFS_SOS_PASSWORD);
        edit.putBoolean(PREFS_SEND_IPC,checkSendIpc.isChecked());
        edit.putBoolean(PREFS_SEND_NET,checkSendNet.isChecked());
        edit.apply();
    }

    private void updateVisibility() {
        if (checkSendNet.isChecked()) {
            editSosServerUrl.setVisibility(View.VISIBLE);
            editSosUsername.setVisibility(View.VISIBLE);
            editSosPassword.setVisibility(View.VISIBLE);
        } else {
            editSosServerUrl.setVisibility(View.GONE);
            editSosUsername.setVisibility(View.GONE);
            editSosPassword.setVisibility(View.GONE);
        }
        checkSendingPrereqs();
    }

    @SuppressLint("SetTextI18n")
    private void checkSendingPrereqs() {
        boolean isEnabled = checkSendIpc.isChecked() || checkSendNet.isChecked();
        if (isEnabled) {
            String failures = getMissingPrereqs();

            if (failures == null) {
                viewMeasurements.setVisibility(View.VISIBLE);
                sendButton.setEnabled(true);
                textCannotSendWarning.setVisibility(View.GONE);
            } else {
                viewMeasurements.setVisibility(View.GONE);
                sendButton.setEnabled(true);
                textCannotSendWarning.setText(failures);
                textCannotSendWarning.setVisibility(View.VISIBLE);
            }
        } else {
            sendButton.setEnabled(false);
            textCannotSendWarning.setText("Send via IPC or Internet must be checked");
            textCannotSendWarning.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateVisibility();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private final static String LINE_SEP = System.getProperty("line.separator");
    private boolean firstLogLine = true;
    private void log(String text) {
        if (text == null)
            return;
        if (textResult.getText().length() > 2000)
            firstLogLine = true;
        if (firstLogLine) {
            firstLogLine = false;
            textResult.setText(text);
        } else {
            textResult.append(LINE_SEP);
            textResult.append(text);
        }
    }

    /**
     * Gets a message describing anything this sensor is missing that would be required
     * to register with an SOS-T server
     * @return null == all prereqs are complete
     */
    private String getMissingPrereqs() {
        StringWriter out = null;
        if (checkSendNet.isChecked()) {
            String url = editSosServerUrl.getText().toString();
            if ((url == null) || (url.length() == 0)) {
                if (out == null)
                    out = new StringWriter();
                else
                    out.append(", ");
                out.append("Missing Server URL");
            }
        }
        if (out == null)
            return null;
        return out.toString();
    }

    @Override
    public void onSosOperationReceived(final AbstractSosOperation operation) {
        if (operation != null) {
            runOnUiThread(() -> {
                log(operation.getClass().getSimpleName() + " received");
                updateVisibility();
            });
            if (operation instanceof OperationGetCapabilitiesResponse) {
                ArrayList<SosSensor> opSensors = ((OperationGetCapabilitiesResponse) operation).getSensors();
                if ((opSensors != null) && !opSensors.isEmpty()) {
                    Log.d(SosIpcTransceiver.TAG,opSensors.size()+" sensors described by server");
                    for (SosSensor sensor:opSensors) {
                        //TODO testing
                        if ((sensor.getId() != null) && sensor.getId().contains("405-TORGI")) {
                            Log.d(SosIpcTransceiver.TAG,"... to include a TORGI sensor. Requesting most recent readings...");
                            sosService.getSensorResultFromServer(sensor);
                            SosSensor current = findSensor(sensor);
                            if (sensors == null)
                                sensors = new ArrayList<>();
                            if (current == null)
                                sensors.add(sensor);
                            else
                                current.update(sensor);
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ((sensors != null) && !sensors.isEmpty()) {
                                StringWriter out = new StringWriter();
                                boolean first = true;
                                for (SosSensor a:sensors) {
                                    if (first)
                                        first = false;
                                    else
                                        out.append("\r\n");
                                    out.append(a.toString());
                                }
                                log(out.toString());
                            } else
                                log("No sensor data received");
                        }
                    });
                }
            }
        }
    }

    private SosSensor findSensor(SosSensor sensor) {
        if ((sensor != null) && (sensors != null) && !sensors.isEmpty()) {
            for (SosSensor current:sensors) {
                if (sensor.isSame(current))
                    return current;
            }
        }
        return null;
    }

    @Override
    public void onSosError(final String message) {
        runOnUiThread(() -> {
            log(message);
        });
    }

    @Override
    public void onSosConfigurationSuccess() {
        runOnUiThread(() -> {
            log("SOS configuration was successful");
        });
    }
}