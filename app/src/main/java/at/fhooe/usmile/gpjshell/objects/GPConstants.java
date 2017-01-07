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
package at.fhooe.usmile.gpjshell.objects;

public class GPConstants {
	public static final byte[] SD_SE_KEYS={0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x48 };
	public static final byte[] DEFAULT_KEYS={0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4F };
	public static final byte[] UICC_SE_KEY_MAC={(byte)0x46,(byte)0x68,(byte)0xf2,(byte)0xe6,(byte)0x3e,(byte)0x37,(byte)0xec,(byte)0xd5,(byte)0x25,(byte)0xf4,(byte)0x8a,(byte)0x62,(byte)0x0d,(byte)0x3d,(byte)0x29,(byte)0xa7};
	public static final byte[] UICC_SE_KEY_ENC={(byte)0x43,(byte)0xa8,(byte)0xab,(byte)0x4a,(byte)0xd0,(byte)0x9b,(byte)0x1a,(byte)0xfe,(byte)0x1c,(byte)0xf2,(byte)0x25,(byte)0x85,(byte)0x67,(byte)0x3d,(byte)0xa1,(byte)0x7c};
	public static final byte[] UICC_SE_KEY_KEK={(byte)0x9e,(byte)0x1f,(byte)0x8f,(byte)0xc8,(byte)0xc1,(byte)0x5b,(byte)0xe5,(byte)0x9d,(byte)0xfd,(byte)0x07,(byte)0xef,(byte)0x80,(byte)0xea,(byte)0xe9,(byte)0xd6,(byte)0xb5};

	public static final byte[] GEMALTO_UICC = {(byte) 0xa0, 0x00, 0x00, 0x00, 0x18,0x43, 0x4D,(byte)0xFF,0x33,(byte)0xFF,(byte)0xFF,(byte)0x89,(byte)0xC0,0x00,0x00};

	public static final String READER_UICC = "SIM - UICC";
	public static final String READER_SDDEVICEFIDELITY = "SD - DeviceFidelity SD Card";
	
	public static final int UICC_KEY_ID = 32;
	public static final int SD_KEY_ID = 0;
	
	
}
