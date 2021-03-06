/*
 * This file is part of MultipathControl.
 *
 * Copyright 2012 UCLouvain - Gregory Detal <first.last@uclouvain.be>
 * Copyright 2015 UCLouvain - Matthieu Baerts <first.last@student.uclouvain.be>
 *
 * MultipathControl is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package be.uclouvain.multipathcontrol.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import be.uclouvain.multipathcontrol.MPCtrl;
import be.uclouvain.multipathcontrol.R;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.services.MainService;
import be.uclouvain.multipathcontrol.stats.JSONSender;
import be.uclouvain.multipathcontrol.system.Sysctl;

import static be.uclouvain.multipathcontrol.services.MainService.getDateDiff;

public class MainActivity extends Activity {

	private MPCtrl mpctrl;
	private Switch multiIfaceSwitch;
	private Switch defaultDataSwitch;
	private Switch dataBackupSwitch;
	private Switch saveBatterySwitch;
	private Switch ipv6Switch;
	private Switch savePowerGPSSwitch;
	private Switch trackingSwitch;
	private Switch trackingSecSwitch;
	private Button tcpCCButton;
	private TextView configIdText;

	private static int getConfigId() {
		/* Return the current configId or -1 */
		final File file = new File(Environment.getExternalStorageDirectory()
				.getAbsolutePath(), MainService.CONFIG_FILE);
		int configId = -1;
		if (!file.exists()) {
			return configId;
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			configId = Integer.parseInt(br.readLine());
			// Don't forget to close the file!
			br.close();

		} catch (FileNotFoundException e) {
			Log.e("MAINACTIVITY", "Config file not found but file exists...");
		} catch (IOException e) {
			Log.e("MAINACTIVITY", "IOException: " + e);
		} catch (NumberFormatException e) {
			Log.e("MAINACTIVITY", "NumberFormatException: " + e);
		}
		return configId;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		multiIfaceSwitch = (Switch) findViewById(R.id.switch_multiiface);
		defaultDataSwitch = (Switch) findViewById(R.id.switch_default_data);
		dataBackupSwitch = (Switch) findViewById(R.id.switch_data_backup);
		saveBatterySwitch = (Switch) findViewById(R.id.switch_save_battery);
		ipv6Switch = (Switch) findViewById(R.id.switch_ipv6);
		savePowerGPSSwitch = (Switch) findViewById(R.id.switch_save_power_gps);
		trackingSwitch = (Switch) findViewById(R.id.switch_tracking);
		trackingSecSwitch = (Switch) findViewById(R.id.switch_tracking_sec);
		tcpCCButton = (Button) findViewById(R.id.button_tcp_cc);
		configIdText = (TextView) findViewById(R.id.textview_config_id);

		mpctrl = Manager.create(getApplicationContext());
		if (mpctrl == null) {
			Toast.makeText(this, "It seems this is not a rooted device",
					Toast.LENGTH_LONG).show();
			moveTaskToBack(true);
			return;
		}

		// do that now, to avoid useless call to onCheckedChangeListerner
		// start a new service if needed
		startService(new Intent(this, MainService.class));

		setChecked();
		multiIfaceSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerMultiIface);
		defaultDataSwitch.setClickable(false);
		//defaultDataSwitch
		//		.setOnCheckedChangeListener(onCheckedChangeListernerDefaultData);
		dataBackupSwitch.setClickable(false);
		//dataBackupSwitch
		//		.setOnCheckedChangeListener(onCheckedChangeListernerDataBackup);
		saveBatterySwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerSaveBattery);
		ipv6Switch.setOnCheckedChangeListener(onCheckedChangeListernerIPv6);
		savePowerGPSSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerSavePowerGPS);
		trackingSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerTracking);
		trackingSecSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerTrackingSec);
		tcpCCButton.setOnClickListener(onClickListenerTcpCC);

		Button testButton = (Button) findViewById(R.id.button_send_data);
		testButton.setOnClickListener(onClickListenerSend);
	}

	@Override
	protected void onResume() {
		super.onResume();

		Config.updateDynamicConfig();
		setChecked();
		int configId = getConfigId();
		configIdText.setText("ConfigId: " + String.valueOf(configId));
	}

	protected void onDestroy() {
		super.onDestroy();
		Manager.destroy(getApplicationContext());
	}

	private void setChecked() {
		multiIfaceSwitch.setChecked(Config.mEnabled);
		defaultDataSwitch.setChecked(Config.defaultRouteData);
		dataBackupSwitch.setChecked(Config.dataBackup);
		saveBatterySwitch.setChecked(Config.saveBattery);
		ipv6Switch.setChecked(Config.ipv6);
		savePowerGPSSwitch.setChecked(Config.savePowerGPS);
		trackingSwitch.setChecked(Config.tracking);
		trackingSecSwitch.setChecked(Config.trackingSec);
		tcpCCButton.setText(getText(R.string.button_tcp_cc) + ": "
				+ Config.tcpcc);
	}

	private OnCheckedChangeListener onCheckedChangeListernerMultiIface = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (mpctrl.setStatus(isChecked) && !isChecked)
				Toast.makeText(
						MainActivity.this,
						"The second interface will be disabled in a few seconds",
						Toast.LENGTH_LONG).show();
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerDefaultData = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setDefaultData(isChecked);
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerDataBackup = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setDataBackup(isChecked);
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerSaveBattery = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (mpctrl.setSaveBattery(isChecked))
				Toast.makeText(
						MainActivity.this,
						"Please disconnect/reconnect cellular interface or reboot",
						Toast.LENGTH_LONG).show();
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerIPv6 = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (Config.ipv6 != isChecked && Sysctl.setIPv6(isChecked)) {
				Config.ipv6 = isChecked;
				Config.saveStatus(getApplicationContext());
			} else
				Toast.makeText(MainActivity.this,
						"Not able to change IPv6 settings",
						Toast.LENGTH_LONG).show();
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerSavePowerGPS = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setSavePowerGPS(isChecked);
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerTracking = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (mpctrl.setTracking(isChecked))
				mpctrl.displayWarningIfNoHostname(isChecked);
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerTrackingSec = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (mpctrl.setTrackingSec(isChecked)) {
				// track with better accuracy
				savePowerGPSSwitch.setChecked(!isChecked);
				mpctrl.displayWarningIfNoHostname(isChecked);
			}
		}
	};

	private OnClickListener onClickListenerTcpCC = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(MainActivity.this, TCPCCActivity.class);
			startActivity(intent);
		}
	};

	private OnClickListener onClickListenerSend = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.d(Manager.TAG, "Send data from button");
			JSONSender.sendAll(getApplicationContext());
		}
	};
}
