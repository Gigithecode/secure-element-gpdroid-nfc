/*******************************************************************************
 * Copyright (c) 2014 Michael Hölzl <mihoelzl@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Michael Hölzl <mihoelzl@gmail.com> - initial implementation
 *     Thomas Sigmund - data base, key set, channel set selection and GET DATA integration
 ******************************************************************************/
package at.fhooe.usmile.gpjshell;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

import net.sourceforge.gpj.cardservices.AID;
import net.sourceforge.gpj.cardservices.AIDRegistryEntry;
import net.sourceforge.gpj.cardservices.GlobalPlatformService;
import net.sourceforge.gpj.cardservices.interfaces.GPTerminal;
import net.sourceforge.gpj.cardservices.interfaces.NfcTerminal;
import net.sourceforge.gpj.cardservices.interfaces.OpenMobileAPITerminal;

import org.simalliance.openmobileapi.Reader;
import org.simalliance.openmobileapi.SEService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint.Cap;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import at.fhooe.usmile.gpjshell.db.ChannelSetDataSource;
import at.fhooe.usmile.gpjshell.db.KeysetDataSource;
import at.fhooe.usmile.gpjshell.objects.GPAppletData;
import at.fhooe.usmile.gpjshell.objects.GPChannelSet;
import at.fhooe.usmile.gpjshell.objects.GPConstants;
import at.fhooe.usmile.gpjshell.objects.GPKeyset;

public class MainActivity extends Activity implements SEService.CallBack,
		TCPFileResultListener {

	private final static String LOG_TAG = "GPJShell";
	public final static int ACTIVITYRESULT_FILESELECTED = 101;
	public final static int ACTIVITYRESULT_KEYSET_SET = 102;
	public final static int ACTIVITYRESULT_CHANNEL_SET = 103;
	public final static int ACTIVITYRESULT_INSTALL_PARAM_SET = 104;
	public final static int ACTIVITYRESULT_GET_DATA = 105;
	public final static int ACTIVITYRESULT_APPLET_INSTALL_TEST = 106;
	private TextView mLog;

	// UI Elements
	private Spinner mReaderSpinner = null;
	private Spinner mKeysetSpinner = null;
	private Spinner mChannelSpinner = null;
	private Button buttonConnect = null;
	private Button mButtonAddKeyset = null;
	private Button mButtonAddChannelSet = null;
	private Button mButtonRemoveKeyset = null;
	private Button mButtonRemoveChannelset = null;
	private Button mButtonGetData = null;
	private Button mButtonTestMifare = null;

	private static LogMe MAIN_Log;

	private GPTerminal mTerminal = null;
	private Button buttonListApplet, buttonSelectApplet,
			mButtonAppletInstallTest;

	private TCPConnection mTCPConnection = null;
	private ArrayAdapter<String> mKeysetAdapter;
	private ArrayAdapter<String> mChannelSetAdapter;

	public enum APDU_COMMAND {
		APDU_INSTALL, APDU_DELETE_SENT_APPLET, APDU_DISPLAYAPPLETS_ONCARD, APDU_SELECT, APDU_SEND, APDU_GET_DATA, APDU_DELETE_SELECTED_APPLET, APDU_CMD_OPEN
	}

	private String mAppletUrl = null;
	private TextView mFileNameView = null;
	private Map<String, GPKeyset> mKeysetMap = null;
	private Map<String, GPChannelSet> mChannelSetMap = null;
	private int mP1 = 0;
	private int mP2 = 0;
	private ConcurrentLinkedQueue<GPCommand> mCommandExecutionQueue = null;

	// NFC
	private NfcAdapter mNfcAdapter;
	private static final int PENDING_INTENT_TECH_DISCOVERED = 1;
	private boolean mWaitingForMfTest;
	private static final int DIALOG_MF_WAIT_FOR_TAG = 1;
	private static final int DIALOG_MF_WAIT_FOR_FINISH = 2;
	private MifareTest mMifareTest = null;

	private Queue<Integer> mInstallStartTimes;
	private Button mButtonEchoTest;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		MAIN_Log = new LogMe();
		mCommandExecutionQueue = new ConcurrentLinkedQueue<GPCommand>();

		mButtonGetData = (Button) findViewById(R.id.btn_get_data);

		mFileNameView = (TextView) findViewById(R.id.text1);
		mButtonAddKeyset = (Button) findViewById(R.id.btn_add_keyset);
		mButtonAddKeyset.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this,
						AddKeysetActivity.class);
				if (mReaderSpinner != null)
					intent.putExtra("readername", mReaderSpinner
							.getSelectedItem().toString());
				startActivityForResult(intent, ACTIVITYRESULT_KEYSET_SET);
			}
		});

		mButtonAddChannelSet = (Button) findViewById(R.id.btn_add_channelset);
		mButtonAddChannelSet.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this,
						AddChannelSetActivity.class);
				startActivityForResult(intent, ACTIVITYRESULT_CHANNEL_SET);
			}
		});

		mButtonRemoveChannelset = (Button) findViewById(R.id.btn_remove_channelset);
		mButtonRemoveChannelset.setOnClickListener(new View.OnClickListener() {
			// Remove actual selected channelset
			@Override
			public void onClick(View v) {
				GPChannelSet channel = mChannelSetMap.get(mChannelSpinner
						.getSelectedItem());
				if (channel != null) {
					ChannelSetDataSource channelSource = new ChannelSetDataSource(
							MainActivity.this);
					channelSource.open();
					channelSource.remove(channel.getChannelNameString());
					channelSource.close();

					mChannelSetAdapter.remove(channel.getChannelNameString());
					mChannelSetAdapter.notifyDataSetChanged();
				}
			}
		});

		mButtonRemoveKeyset = (Button) findViewById(R.id.btn_remove_keyset);
		mButtonRemoveKeyset.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				GPKeyset keyset = mKeysetMap.get(mKeysetSpinner
						.getSelectedItem());
				if (keyset != null) {
					KeysetDataSource keysetSource = new KeysetDataSource(
							MainActivity.this);
					keysetSource.open();
					keysetSource.remove(keyset.getUniqueID());
					keysetSource.close();

					Log.d(LOG_TAG, "keyset count" + mKeysetAdapter.getCount()
							+ "name " + keyset.getName());
					for (int i = 0; i < mKeysetAdapter.getCount(); i++) {
						Log.d(LOG_TAG,
								"keyset name " + mKeysetAdapter.getItem(i));
					}
					mKeysetAdapter.remove(keyset.getDisplayName());

					Log.d(LOG_TAG, "keyset count" + mKeysetAdapter.getCount());
					mKeysetAdapter.notifyDataSetChanged();
				}
			}
		});

		mButtonTestMifare = (Button) findViewById(R.id.btn_test_mf);
		mButtonTestMifare.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				mWaitingForMfTest = true;
				MainActivity.this.showDialog(DIALOG_MF_WAIT_FOR_TAG);
			}
		});

		loadPreferences();

		mLog = (TextView) findViewById(R.id.log);
		mLog.setMovementMethod(new ScrollingMovementMethod());

		MAIN_Log.d(LOG_TAG, "Start GPJ Shell");
		// GlobalPlatformService.usage();

	}

	protected Dialog onCreateDialog(int id, Bundle args) {
		switch (id) {
		case DIALOG_MF_WAIT_FOR_TAG:
			return new AlertDialog.Builder(this)
					.setTitle("Mifare Test")
					.setMessage("Touch Mifare tag to start test")
					.setCancelable(true)
					.setOnCancelListener(
							new DialogInterface.OnCancelListener() {

								@Override
								public void onCancel(DialogInterface dialog) {
									mWaitingForMfTest = false;

								}
							}).create();

		case DIALOG_MF_WAIT_FOR_FINISH:
			return new AlertDialog.Builder(this)
					.setTitle("Mifare Test")
					.setMessage("Test running, please wait...")
					.setCancelable(true)
					.setOnCancelListener(
							new DialogInterface.OnCancelListener() {

								@Override
								public void onCancel(DialogInterface dialog) {
									MAIN_Log.d(LOG_TAG, "cancelled");
									if (mMifareTest != null
											&& mMifareTest.isRunning()) {
										mMifareTest.cancel(true);

									}
								}
							}).create();

		}
		return null;
	}

	private void loadPreferences() {

		AppPreferences prefs = new AppPreferences(getApplicationContext());
		if (!("".equals(prefs.getSelectedCap()))) {
			mAppletUrl = prefs.getSelectedCap();
			mFileNameView.setText(Uri.parse(mAppletUrl).getLastPathSegment());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		NfcManager nMan = (NfcManager) this
				.getSystemService(Context.NFC_SERVICE);
		mNfcAdapter = nMan.getDefaultAdapter();
		PendingIntent pi = createPendingResult(PENDING_INTENT_TECH_DISCOVERED,
				new Intent(), 0);
		mNfcAdapter
				.enableForegroundDispatch(
						this,
						pi,
						new IntentFilter[] { new IntentFilter(
								NfcAdapter.ACTION_TECH_DISCOVERED) },
						new String[][] {
								new String[] { android.nfc.tech.MifareClassic.class
										.getName() },
								new String[] { android.nfc.tech.IsoDep.class
										.getName() } });

		if (mTerminal == null) {
			mTerminal = // new OpenMobileAPITerminal(this, this);
			NfcTerminal.getInstance(this);
		}
		mTCPConnection = new TCPConnection(this, this);
		Thread td = new Thread(mTCPConnection);
		td.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d("Michi", "onpause applet list");
		if (mTerminal != null) {
			mTerminal.shutdown();
		}
		mNfcAdapter.disableForegroundDispatch(this);
		if (mTCPConnection != null) {
			mTCPConnection.stopConnection();
		}
	}

	@Override
	protected void onDestroy() {
		if (mTerminal != null) {
			mTerminal.shutdown();
		}
		if (mTCPConnection != null) {
			mTCPConnection.stopConnection();
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		if (_resultCode == Activity.RESULT_OK) {

			switch (_requestCode) {
			case ACTIVITYRESULT_FILESELECTED:
				Uri uri = _data.getData();
				MAIN_Log.d(LOG_TAG, "File Uri: " + uri.toString());
				mAppletUrl = uri.toString();
				new AppPreferences(getApplicationContext())
						.saveSelectedCap(mAppletUrl);

				mFileNameView.setText(uri.getLastPathSegment());
				// performCommand(APDU_COMMAND.APDU_INSTALL,
				// mReaderSpinner.getSelectedItemPosition(), uri.toString());
				break;

			case ACTIVITYRESULT_KEYSET_SET:
				GPKeyset keyset = (GPKeyset) _data.getExtras().get(
						GPKeyset.KEYSET);
				// set actual reader to keyset - each keyset is bound to a
				// reader
				keyset.setReaderName((String) mReaderSpinner.getSelectedItem());

				KeysetDataSource keySource = new KeysetDataSource(this);

				keySource.open();
				keySource.insertKeyset(keyset);
				mKeysetMap = keySource.getKeysets((String) mReaderSpinner
						.getSelectedItem());
				keySource.close();

				addKeysetItemsOnSpinner(Arrays.asList(mKeysetMap.keySet()
						.toArray(new String[0])));

				break;

			case ACTIVITYRESULT_CHANNEL_SET:
				GPChannelSet channel = (GPChannelSet) _data.getExtras().get(
						GPChannelSet.CHANNEL_SET);

				ChannelSetDataSource channelSource = new ChannelSetDataSource(
						this);

				channelSource.open();
				channelSource.insertChannelSet(channel);
				mChannelSetMap = channelSource.getChannelSets();
				channelSource.close();

				addChannelSetItemsOnSpinner(Arrays.asList(mChannelSetMap
						.keySet().toArray(new String[0])));

				break;

			case ACTIVITYRESULT_INSTALL_PARAM_SET:
				byte[] params = null;
				byte privileges = 0;
				if (_data != null) {
					_data.getExtras().getByteArray("params");
					_data.getExtras().getByte("privileges");
				}

				try {
					performCommand(APDU_COMMAND.APDU_INSTALL,
							mReaderSpinner.getSelectedItemPosition(), params,
							privileges, mAppletUrl);
				} catch (Exception e) {
					MAIN_Log.e(LOG_TAG, "Error while installing: ", e);
					e.printStackTrace();
				}
				break;

			case ACTIVITYRESULT_GET_DATA:
				mP1 = _data.getExtras().getInt("p1");
				mP2 = _data.getExtras().getInt("p2");
				MAIN_Log.d("Parameters: ", "P1=" + mP1 + ", P2=" + mP2);
				Handler handler = new Handler();
				handler.postDelayed(new Runnable() {

					@Override
					public void run() {
						Integer params[] = { mP1, mP2 };
						performCommand(APDU_COMMAND.APDU_GET_DATA,
								mReaderSpinner.getSelectedItemPosition(), null,
								(byte) 0, params);
					}
				}, 000);
				break;

			case PENDING_INTENT_TECH_DISCOVERED:
				MAIN_Log.d(LOG_TAG, "card discovered");
				if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(_data.getAction())) {
					Tag tag = _data.getParcelableExtra(NfcAdapter.EXTRA_TAG);
					if (mWaitingForMfTest) {
						mWaitingForMfTest = false;

						runMifareTest(tag);
					} else {
						if (mTerminal != null) {
							((NfcTerminal) mTerminal).passTag(tag);
							if (mTerminal.isConnected()) {
								serviceConnected(null);
								Log.d(LOG_TAG, "Card detected");
							}
						}
					}
				}
				break;

			case ACTIVITYRESULT_APPLET_INSTALL_TEST:

			default:
				break;
			}

		} else if (_resultCode == Activity.RESULT_CANCELED) {
			MAIN_Log.d(LOG_TAG, "Result not valid");
		}

	}

	private void runMifareTest(Tag tag) {

		MifareClassic mf = MifareClassic.get(tag);

		mMifareTest = new MifareTest(mf, this, MAIN_Log);

		mMifareTest.execute();
		dismissDialog(DIALOG_MF_WAIT_FOR_TAG);
		showDialog(DIALOG_MF_WAIT_FOR_FINISH);
	}

	/**
	 * sets items to the spinner of keysets
	 * 
	 * @param keysets
	 *            keysets from DB according to the set smartcard
	 */
	public void addKeysetItemsOnSpinner(List<String> keysets) {
		mKeysetSpinner = (Spinner) findViewById(R.id.keyset_spinner);

		// add list to a new initialized list, else elements are not removable
		// from adapter later
		List<String> keysetList = new ArrayList<String>();
		keysetList.addAll(keysets);

		mKeysetAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, keysetList);
		mKeysetAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mKeysetAdapter.setNotifyOnChange(true);

		mKeysetSpinner.setAdapter(mKeysetAdapter);
		mKeysetAdapter.notifyDataSetChanged();
	}

	/**
	 * sets the channelsettings to the channelspinner
	 * 
	 * @param channelSets
	 *            all available channelsets from DB
	 */
	public void addChannelSetItemsOnSpinner(List<String> channelSets) {
		mChannelSpinner = (Spinner) findViewById(R.id.channel_spinner);

		// add list to a new initialized list, else elements are not removable
		// from adapter later
		List<String> channelSetList = new ArrayList<String>();
		channelSetList.addAll(channelSets);

		mChannelSetAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, channelSetList);
		mChannelSetAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mChannelSpinner.setAdapter(mChannelSetAdapter);
		mChannelSetAdapter.notifyDataSetChanged();
	}

	// add items into spinner dynamically
	public void addReaderItemsOnSpinner(Reader[] _readers) {

		mReaderSpinner = (Spinner) findViewById(R.id.reader_spinner);
		buttonConnect = (Button) findViewById(R.id.btn_install_applet);
		buttonListApplet = (Button) findViewById(R.id.btn_list_applets);
		buttonSelectApplet = (Button) findViewById(R.id.button3);
		mButtonAppletInstallTest = (Button) findViewById(R.id.btn_applet_test);
		mButtonEchoTest = (Button) findViewById(R.id.btn_echo_test);

		if (mReaderSpinner != null) {
			List<String> list = new ArrayList<String>();
			for (int i = 0; i < _readers.length; i++) {
				Reader reader = _readers[i];

				list.add(reader.getName());
			}
			if (_readers.length == 0) {
				list.add("NFC Interface");
			}
			ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_spinner_item, list);
			dataAdapter
					.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mReaderSpinner.setAdapter(dataAdapter);

			// refresh keyset spinner when new reader is selected
			mReaderSpinner
					.setOnItemSelectedListener(new OnItemSelectedListener() {

						@Override
						public void onItemSelected(AdapterView<?> arg0,
								View arg1, int arg2, long arg3) {
							KeysetDataSource source = new KeysetDataSource(
									MainActivity.this);
							source.open();
							mKeysetMap = source
									.getKeysets((String) mReaderSpinner
											.getSelectedItem());
							source.close();
							addKeysetItemsOnSpinner(Arrays.asList(mKeysetMap
									.keySet().toArray(new String[0])));

							ChannelSetDataSource channelSource = new ChannelSetDataSource(
									MainActivity.this);
							channelSource.open();
							mChannelSetMap = channelSource.getChannelSets();
							channelSource.close();
							addChannelSetItemsOnSpinner(Arrays
									.asList(mChannelSetMap.keySet().toArray(
											new String[0])));
						}

						@Override
						public void onNothingSelected(AdapterView<?> arg0) {
						}
					});

			buttonConnect.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent intent = new Intent(MainActivity.this,
							SetInstallParamActivity.class);
					startActivityForResult(intent,
							ACTIVITYRESULT_INSTALL_PARAM_SET);

				}
			});
			buttonSelectApplet.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("file/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);

					startActivityForResult(Intent.createChooser(intent,
							"Select a File to Upload"),
							ACTIVITYRESULT_FILESELECTED);
				}

			});

			mButtonAppletInstallTest.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mTerminal.isConnected()) {
						MAIN_Log.d(LOG_TAG, "starting applet install test");

						Intent intent = new Intent(MainActivity.this,
								AppletInstallTest.class);

						// to initialize the connection's keyset
						performCommand(APDU_COMMAND.APDU_CMD_OPEN, 0, null,
								(byte) 0, null);

						intent.putExtra(AppletInstallTest.EXTRA_RUNS, 5);
						intent.putExtra(AppletInstallTest.EXTRA_APPLET_URI,
								mAppletUrl);
						GPKeyset keyset = mKeysetMap
								.get((String) mKeysetSpinner.getSelectedItem());
						GPChannelSet channelSet = mChannelSetMap
								.get((String) mChannelSpinner.getSelectedItem());
						intent.putExtra(AppletInstallTest.EXTRA_CHANNELSET,
								channelSet);
						intent.putExtra(AppletInstallTest.EXTRA_KEYSET, keyset);

						startActivity(intent);

					} else {
						MAIN_Log.d(LOG_TAG, "No card available for test");
					}

				}

			});

			mButtonEchoTest.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mTerminal.isConnected()) {
						MAIN_Log.d(LOG_TAG, "starting echo test");

						Intent intent = new Intent(MainActivity.this,
								ApduEchoTest.class);

						intent.putExtra(ApduEchoTest.EXTRA_RUNS, 1000);
						intent.putExtra(ApduEchoTest.EXTRA_STEP_SIZE, 4);
						intent.putExtra(ApduEchoTest.EXTRA_STEPS, 63);

						startActivity(intent);

					} else {
						MAIN_Log.d(LOG_TAG, "No card available for test");
					}

				}

			});
			buttonListApplet.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					performCommand(APDU_COMMAND.APDU_DISPLAYAPPLETS_ONCARD,
							mReaderSpinner.getSelectedItemPosition(), null,
							(byte) 0, null);

				}
			});

			mButtonGetData.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent intent = new Intent(MainActivity.this,
							GetDataActivity.class);
					startActivityForResult(intent, ACTIVITYRESULT_GET_DATA);

				}
			});
		}
	}

	public void serviceConnected(SEService _session) {
		Reader[] readers = new Reader[0];

		if (mTerminal instanceof OpenMobileAPITerminal) {

			readers = ((OpenMobileAPITerminal) mTerminal).getReaders();
		}
		addReaderItemsOnSpinner(readers);

		// --------- ADD DEFAULT KEYS TO DB -------------

		KeysetDataSource keysetSource = new KeysetDataSource(this);
		keysetSource.open();
		for (int i = 1; i <= readers.length; i++) {
			Reader reader = readers[i - 1];
			// set unique id to -1. it will be set by DB later, because -1 will
			// not be found
			GPKeyset defaultKeyset = new GPKeyset(-1, "Default", 0, 0,
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					reader.getName());

			keysetSource.insertKeyset(defaultKeyset);
		}

		if (readers.length == 0) {
			GPKeyset defaultKeyset = new GPKeyset(-1, "Default", 0, 0,
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					GPUtils.byteArrayToString(GPConstants.DEFAULT_KEYS),
					"NFC Interface");
			keysetSource.insertKeyset(defaultKeyset);
		}

		// initialize keysetmap
		mKeysetMap = keysetSource.getKeysets((String) mReaderSpinner
				.getSelectedItem());

		keysetSource.close();

		ChannelSetDataSource channelSource = new ChannelSetDataSource(this);
		channelSource.open();
		channelSource.insertChannelSet(new GPChannelSet("Default",
				GlobalPlatformService.SCP_ANY, 3, false));

		// initialize channelmap
		mChannelSetMap = channelSource.getChannelSets();
		channelSource.close();

		// ------------ END ADDING DEFAULT ------------

		/**
		 * Check if there is a command to exeucte
		 */
		while (!mCommandExecutionQueue.isEmpty()) {
			new PerformCommandTask().execute(mCommandExecutionQueue.poll());
		}
	}

	/**
	 * performs selected command from APDU enum params and privileges are
	 * necessary for new installations, else they may be set to null
	 * 
	 * @param _cmd
	 *            APDU-enum command
	 * @param _seekReader
	 *            actual selected reader
	 * @param params
	 *            necessary for installations, else null
	 * @param privileges
	 *            necessary for installations, else (byte) 0
	 */
	private void performCommand(APDU_COMMAND _cmd, int _seekReader,
			byte[] _params, byte _privileges, Object _cmdParam) {
		GPCommand c = new GPCommand(_cmd, _seekReader, _params, _privileges,
				_cmdParam);
		c.setReaderName(mReaderSpinner.getSelectedItem().toString());
		if (mTerminal.isConnected()) {
			new PerformCommandTask().execute(c);
		} else {
			mCommandExecutionQueue.add(c);
		}
	}

	@Override
	public void fileReceived(String _url, int _reader, int _keyset,
			int _securechannelset) {
		mAppletUrl = _url;
		mReaderSpinner.setSelection(_reader);
		mKeysetSpinner.setSelection(_keyset);
		mChannelSpinner.setSelection(_securechannelset);

		performCommand(APDU_COMMAND.APDU_DELETE_SENT_APPLET,
				mReaderSpinner.getSelectedItemPosition(), null, (byte) 0,
				mAppletUrl);
		performCommand(APDU_COMMAND.APDU_INSTALL,
				mReaderSpinner.getSelectedItemPosition(), null, (byte) 0,
				mAppletUrl);

	}

	private class PerformCommandTask extends AsyncTask<GPCommand, Void, String> {
		@Override
		protected String doInBackground(GPCommand... _cmd) {
			GPKeyset keyset = mKeysetMap.get((String) mKeysetSpinner
					.getSelectedItem());
			GPChannelSet channelSet = mChannelSetMap
					.get((String) mChannelSpinner.getSelectedItem());

			if (_cmd.length <= 0)
				return null;

			String ret = null;

			ret = GPConnection.getInstance(MainActivity.this).performCommand(
					mTerminal, keyset, channelSet, _cmd[0]);
			return ret;
		}

		protected void onPostExecute(String _resultString) {
			MAIN_Log.d(LOG_TAG, _resultString);
		}
	}

	public static LogMe log() {
		return MAIN_Log;
	}

	public class LogMe {
		public void log(String _tag, String _text) {
			Log.d(_tag, _text);
			String[] lines = _text.split("<br>|<br/>");
			for (String line : lines) {
				mLog.append(Html.fromHtml("<font color=\"#ff0000\">" + _tag
						+ "</font> : " + line + "<br>"));
			}
		}

		public void e(String _tag, String _text) {
			log(_tag, _text);
		}

		public void e(String _tag, String _text, Exception _e) {
			log(_tag, _text + _e.getMessage());
		}

		public void d(String _tag, String _text) {
			log(_tag, _text);
		}

		public void i(String _tag, String _text) {
			log(_tag, _text);
		}
	}

	public void mifareTestFinished() {
		dismissDialog(DIALOG_MF_WAIT_FOR_FINISH);
	}

	public void stopTimer() {

		long start = mInstallStartTimes.remove();
		MAIN_Log.d(LOG_TAG, "timer stopped: "
				+ (SystemClock.elapsedRealtime() - start));
	}

}
