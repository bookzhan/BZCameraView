/*
 *              Copyright (c) 2016-2019, Nuralogix Corp.
 *                      All Rights reserved
 *
 *      THIS SOFTWARE IS LICENSED BY AND IS THE CONFIDENTIAL AND
 *      PROPRIETARY PROPERTY OF NURALOGIX CORP. IT IS
 *      PROTECTED UNDER THE COPYRIGHT LAWS OF THE USA, CANADA
 *      AND OTHER FOREIGN COUNTRIES. THIS SOFTWARE OR ANY
 *      PART THEREOF, SHALL NOT, WITHOUT THE PRIOR WRITTEN CONSENT
 *      OF NURALOGIX CORP, BE USED, COPIED, DISCLOSED,
 *      DECOMPILED, DISASSEMBLED, MODIFIED OR OTHERWISE TRANSFERRED
 *      EXCEPT IN ACCORDANCE WITH THE TERMS AND CONDITIONS OF A
 *      NURALOGIX CORP SOFTWARE LICENSE AGREEMENT.
 */


package com.luoye.bzcamera.utils;

public enum CameraCapacityCheckResult {
    GOOD, ERROR, NOT_SUPPORT_CAMERA2, MAX_PIXEL_LESS,
    ISO_ADJUSTABLE_RANGE_UNAVAILABLE, ISO_ADJUST_UNAVAILABLE,
    HARDWARE_LEVEL_LESS, NOT_ENOUGH_FPS
}
