package com.ru.cordova.printer.bluetooth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.util.Base64;

public class BluetoothPrinter extends CordovaPlugin {
	private String LOG_TAG = "BluetoothPrinter";
    private BluetoothSocket mmSocket;
	private BluetoothDevice mmDevice;
	private OutputStream mmOutputStream;
	private InputStream mmInputStream;
    private byte[] readBuffer;
	private int readBufferPosition;
	int counter;
	private volatile boolean stopWorker;
    private byte[] LINE_FEED = new byte[]{0x0A};

	private Bitmap bitmap;

	public BluetoothPrinter() {}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;

		} else if (action.equals("connect")) {
			String name = args.getString(0);
			if (findBT(callbackContext, name)) {
				try {
					connectBT(callbackContext);
				} catch (IOException e) {
					Log.e(LOG_TAG, e.getMessage());
					e.printStackTrace();
				}
			} else {
				callbackContext.error("Bluetooth Device Not Found: " + name);
			}
			return true;

		} else if (action.equals("disconnect")) {
            try {
                disconnectBT(callbackContext);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;

        } else if (action.equals("print") || action.equals("printImage")) {
			try {
				String msg = args.getString(0);
				printImage(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;

		} else if (action.equals("printText")) {
            try {
                String msg = args.getString(0);
                printText(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;

        } else if (action.equals("printPOSCommand")) {
			try {
				String msg = args.getString(0);
                printPOSCommand(callbackContext, hexStringToBytes(msg));
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;

		} else if(action.equals("printLn")) {
            try {
                printLn(callbackContext, args.getInt(0));
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
        }

		return false;
	}

    //This will return the array list of paired bluetooth printers
	private void listBT(CallbackContext callbackContext) {
		BluetoothAdapter mBluetoothAdapter;
		String errMsg;
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				errMsg = "No bluetooth adapter available";
				Log.e(LOG_TAG, errMsg);
				callbackContext.error(errMsg);
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
					/*
					Hashtable map = new Hashtable();
					map.put("type", device.getType());
					map.put("address", device.getAddress());
					map.put("name", device.getName());
					JSONObject jObj = new JSONObject(map);
					*/
					json.put(device.getName());
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("No Bluetooth Device Found");
			}
			//Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
	}

	// This will find a bluetooth printer device
	private boolean findBT(CallbackContext callbackContext, String name) {
		try {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					if (device.getName().equalsIgnoreCase(name)) {
						mmDevice = device;
						return true;
					}
				}
			}
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


	/**
	 * Tries to open a connection to the bluetooth printer device
	 * @param callbackContext Cordova context
	 * @return boolean
	 * @throws IOException
	 */
	private boolean connectBT(CallbackContext callbackContext) throws IOException {
		try {
			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			beginListenForData();
			//Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


	/**
	 * After opening a connection to bluetooth printer device,
	 * we have to listen and check if a data were sent to be printed.
	 */
	private void beginListenForData() {
		try {
			final Handler handler = new Handler();
			// This is the ASCII code for a newline character
			final byte delimiter = 10;
			stopWorker = false;
			readBufferPosition = 0;
			readBuffer = new byte[1024];
            Thread workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            int bytesAvailable = mmInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
										/*
										final String data = new String(encodedBytes, "US-ASCII");
										readBufferPosition = 0;
										handler.post(new Runnable() {
											public void run() {
												myLabel.setText(data);
											}
										});
                                        */
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            stopWorker = true;
                        }
                    }
                }
            });
			workerThread.start();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * This will send data to bluetooth printer
	 * @param callbackContext Cordova context
	 * @param msg texto recibido
	 * @return boolean
	 * @throws IOException
	 */
	private boolean printText(CallbackContext callbackContext, String msg) throws IOException {
		try {
			mmOutputStream.write(msg.getBytes());
            //mmOutputStream.write(LINE_FEED);
            
			callbackContext.success("Data Sent");
			return true;

		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


    /**
     * Imprimir saltos de lineas
     * @param cantVeces cant lineas a repetir
     */
	private boolean printLn(CallbackContext callbackContext, int cantVeces) {
        try {
            int i;
            for(i=0; i<cantVeces; i++) {
                mmOutputStream.write(LINE_FEED);
            }

            callbackContext.success("Line break printed");
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }
        return false;
    }


	//This will send data to bluetooth printer
    private boolean printImage(CallbackContext callbackContext, String msg) throws IOException {
        try {
            String pureBase64Encoded = msg.substring(msg.indexOf(",")  + 1);

            byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            int mWidth = bitmap.getWidth();
            int mHeight = bitmap.getHeight();
            //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
            bitmap=resizeImage(bitmap, 48 * 8, mHeight);


            byte[]  bt =getBitmapData(bitmap);

            bitmap.recycle();

            mmOutputStream.write(bt);

            // tell the user data were sent
            //Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;


        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }


    private boolean printPOSCommand(CallbackContext callbackContext, byte[] buffer) throws IOException {
        try {
            //mmOutputStream.write(("Inam").getBytes());
            //mmOutputStream.write((((char)0x0A) + "10 Rehan").getBytes());
            mmOutputStream.write(buffer);
            //mmOutputStream.write(0x0A);

            // tell the user data were sent
			Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

	// disconnect bluetooth printer.
	private boolean disconnectBT(CallbackContext callbackContext) throws IOException {
		try {
			stopWorker = true;
			mmOutputStream.close();
			mmInputStream.close();
			mmSocket.close();
			callbackContext.success("Bluetooth Disconnect");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


	private byte[] getText(String textStr) {
        // TODO Auto-generated method stubbyte[] send;
        byte[] send=null;
        try {
            send = textStr.getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            send = textStr.getBytes();
        }
        return send;
    }

    private static byte[] hexStringToBytes(String hexString) {
        hexString = hexString.toLowerCase();
        String[] hexStrings = hexString.split(" ");
        byte[] bytes = new byte[hexStrings.length];
        for (int i = 0; i < hexStrings.length; i++) {
            char[] hexChars = hexStrings[i].toCharArray();
            bytes[i] = (byte) (charToByte(hexChars[0]) << 4 | charToByte(hexChars[1]));
        }
        return bytes;
    }

    private static byte charToByte(char c) {
		return (byte) "0123456789abcdef".indexOf(c);
	}


	private byte[] getImage(Bitmap bitmap) {
        // TODO Auto-generated method stub
        int mWidth = bitmap.getWidth();
        int mHeight = bitmap.getHeight();
        bitmap=resizeImage(bitmap, 48 * 8, mHeight);
        //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
        /*
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        int[] mIntArray = new int[mWidth * mHeight];
        bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
        byte[]  bt =getBitmapData(mIntArray, mWidth, mHeight);*/

        byte[]  bt =getBitmapData(bitmap);


        /*try {//?????????????????
            createFile("/sdcard/demo.txt",bt);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/


        ////byte[]  bt =StartBmpToPrintCode(bitmap);

        bitmap.recycle();
        return bt;
    }

    private static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if(width>w)
        {
            float scaleWidth = ((float) w) / width;
            float scaleHeight = ((float) h) / height+24;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleWidth);
            return Bitmap.createBitmap(bitmap, 0, 0, width,
                    height, matrix, true);
        }else{
            Bitmap resizedBitmap = Bitmap.createBitmap(w, height+24, Config.RGB_565);
            Canvas canvas = new Canvas(resizedBitmap);
            Paint paint = new Paint();
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, (w-width)/2, 0, paint);
            return resizedBitmap;
        }
    }

    private static byte[] getBitmapData(Bitmap bitmap) {
		byte temp = 0;
		int j = 7;
		int start = 0;
		if (bitmap != null) {
			int mWidth = bitmap.getWidth();
			int mHeight = bitmap.getHeight();

			int[] mIntArray = new int[mWidth * mHeight];
			bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
			bitmap.recycle();
			byte []data=encodeYUV420SP(mIntArray, mWidth, mHeight);
			byte[] result = new byte[mWidth * mHeight / 8];
			for (int i = 0; i < mWidth * mHeight; i++) {
				temp = (byte) ((byte) (data[i] << j) + temp);
				j--;
				if (j < 0) {
					j = 7;
				}
				if (i % 8 == 7) {
					result[start++] = temp;
					temp = 0;
				}
			}
			if (j != 7) {
				result[start++] = temp;
			}

			int aHeight = 24 - mHeight % 24;
			int perline = mWidth / 8;
			byte[] add = new byte[aHeight * perline];
			byte[] nresult = new byte[mWidth * mHeight / 8 + aHeight * perline];
			System.arraycopy(result, 0, nresult, 0, result.length);
			System.arraycopy(add, 0, nresult, result.length, add.length);

			byte[] byteContent = new byte[(mWidth / 8 + 4)
					* (mHeight + aHeight)];//
			byte[] bytehead = new byte[4];//
			bytehead[0] = (byte) 0x1f;
			bytehead[1] = (byte) 0x10;
			bytehead[2] = (byte) (mWidth / 8);
			bytehead[3] = (byte) 0x00;
			for (int index = 0; index < mHeight + aHeight; index++) {
				System.arraycopy(bytehead, 0, byteContent, index
						* (perline + 4), 4);
				System.arraycopy(nresult, index * perline, byteContent, index
						* (perline + 4) + 4, perline);
			}
			return byteContent;
		}
		return null;

	}

	private static byte[] encodeYUV420SP(int[] rgba, int width, int height) {
		final int frameSize = width * height;
		byte[] yuv420sp=new byte[frameSize];
		int[] U, V;
		U = new int[frameSize];
		V = new int[frameSize];
		final int uvwidth = width / 2;
		int r, g, b, y, u, v;
		int bits = 8;
		int index = 0;
		int f = 0;
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				r = (rgba[index] & 0xff000000) >> 24;
				g = (rgba[index] & 0xff0000) >> 16;
				b = (rgba[index] & 0xff00) >> 8;
				// rgb to yuv
				y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
				u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
				v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
				// clip y
				// yuv420sp[index++] = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 :
				// y));
				byte temp = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 : y));
				yuv420sp[index++] = temp > 0 ? (byte) 1 : (byte) 0;

				// {
				// if (f == 0) {
				// yuv420sp[index++] = 0;
				// f = 1;
				// } else {
				// yuv420sp[index++] = 1;
				// f = 0;
				// }

				// }

			}

		}
		f = 0;
		return yuv420sp;
	}
}
