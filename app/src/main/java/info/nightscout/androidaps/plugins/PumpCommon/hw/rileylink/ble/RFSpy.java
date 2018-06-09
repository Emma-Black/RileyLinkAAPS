package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble;

import android.os.SystemClock;

import com.gxwtech.roundtrip2.util.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RadioResponse;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.CC111XRegister;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RFSpyCommand;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RXFilterMode;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ThreadUtil;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpy {

    private static final Logger LOG = LoggerFactory.getLogger(RFSpy.class);

    //    public static final byte RFSPY_GET_STATE = 1;
    //    public static final byte RFSPY_GET_VERSION = 2;
    //    public static final byte RFSPY_GET_PACKET = 3; // aka Listen, receive
    //    public static final byte RFSPY_SEND = 4;
    //    public static final byte RFSPY_SEND_AND_LISTEN = 5;
    //    public static final byte RFSPY_UPDATE_REGISTER = 6;
    //    public static final byte RFSPY_RESET = 7;

    public static final long RILEYLINK_FREQ_XTAL = 24000000;


    public static final int EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500; // 1500

    private RileyLinkBLE rileyLinkBle;
    private RFSpyReader reader;
    private int previousRegion = 0;
    private RileyLinkTargetFrequency selectedTargetFrequency;

    private UUID radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
    private UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
    private UUID radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION);
    private UUID responseCountUUID = UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT);


    public RFSpy(RileyLinkBLE rileyLinkBle) {
        this.rileyLinkBle = rileyLinkBle;
        this.rileyLinkBle.setRFSpy(this);
        reader = new RFSpyReader(rileyLinkBle);
    }


    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    public void startReader() {
        rileyLinkBle.registerRadioResponseCountNotification(new Runnable() {
            @Override
            public void run() {
                newDataIsAvailable();
            }
        });
        reader.start();
    }


    // Call this from the "response count" notification handler.
    public void newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        reader.newDataIsAvailable();
    }


    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    public String getVersion() {
        BLECommOperationResult result = rileyLinkBle.readCharacteristic_blocking(radioServiceUUID, radioVersionUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            return StringUtil.fromBytes(result.value);
        } else {
            LOG.error("getVersion failed with code: " + result.resultCode);
            return "(null)";
        }
    }


    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private RFSpyResponse writeToData(byte[] bytes, int responseTimeout_ms) {
        SystemClock.sleep(100);
        // FIXME drain read queue?
        byte[] junkInBuffer = reader.poll(0);

        while (junkInBuffer != null) {
            LOG.warn(ThreadUtil.sig() + "writeToData: draining read queue, found this: " + ByteUtil.shortHexString(junkInBuffer));
            junkInBuffer = reader.poll(0);
        }

        // prepend length, and send it.
        byte[] prepended = ByteUtil.concat(new byte[]{(byte) (bytes.length)}, bytes);
        BLECommOperationResult writeCheck = rileyLinkBle.writeCharacteristic_blocking(radioServiceUUID, radioDataUUID, prepended);
        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            LOG.error("BLE Write operation failed, code=" + writeCheck.resultCode);
            return new RFSpyResponse(); // will be a null (invalid) response
        }
        SystemClock.sleep(100);
        //Log.i(TAG,ThreadUtil.sig()+String.format(" writeToData:(timeout %d) %s",(responseTimeout_ms),ByteUtil.shortHexString(prepended)));
        byte[] rawResponse = reader.poll(responseTimeout_ms);
        RFSpyResponse resp = new RFSpyResponse(rawResponse);
        if (rawResponse == null) {
            LOG.error("writeToData: No response from RileyLink");
        } else {
            if (resp.wasInterrupted()) {
                LOG.error("writeToData: RileyLink was interrupted");
            } else if (resp.wasTimeout()) {
                LOG.error("writeToData: RileyLink reports timeout");
            } else if (resp.isOK()) {
                LOG.warn("writeToData: RileyLink reports OK");
            } else {
                if (resp.looksLikeRadioPacket()) {
                    RadioResponse radioResp = resp.getRadioResponse();
                    byte[] responsePayload = radioResp.getPayload();
                    LOG.info("writeToData: decoded radio response is " + ByteUtil.shortHexString(responsePayload));
                }
                //Log.i(TAG, "writeToData: raw response is " + ByteUtil.shortHexString(rawResponse));
            }
        }
        return resp;
    }


    public RFSpyResponse getRadioVersion() {
        RFSpyResponse resp = writeToData(getCommandArray(RFSpyCommand.GetVersion, null), 1000);
        if (resp == null) {
            LOG.error("getRadioVersion returned null");
        }
        /*
        Log.d(TAG,"checking response count");
        BLECommOperationResult checkRC = rileyLinkBle.readCharacteristic_blocking(radioServiceUUID,responseCountUUID);
        if (checkRC.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            Log.d(TAG,"Response count is: " + ByteUtil.shortHexString(checkRC.value));
        } else {
            LOG.error("Error getting response count, code is " + checkRC.resultCode);
        }
        */
        return resp;
    }


    public RFSpyResponse transmit(RadioPacket radioPacket) {

        return transmit(radioPacket, (byte) 0, (byte) 0, (byte) 0xFF);
    }


    public RFSpyResponse transmit(RadioPacket radioPacket, byte sendChannel, byte repeatCount, byte delay_ms) {
        // append checksum, encode data, send it.
        byte[] fullPacket = ByteUtil.concat(getCommandArray(RFSpyCommand.Send, getByteArray(sendChannel, repeatCount, delay_ms)), radioPacket.getEncoded());
        RFSpyResponse response = writeToData(fullPacket, delay_ms + EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
        return response;
    }


    public RFSpyResponse receive(byte listenChannel, int timeout_ms, byte retryCount) {
        int receiveDelay = timeout_ms * (retryCount + 1);
        byte[] listen = getCommandArray(RFSpyCommand.GetPacket, getByteArray(listenChannel, (byte) ((timeout_ms >> 24) & 0x0FF), (byte) ((timeout_ms >> 16) & 0x0FF), (byte) ((timeout_ms >> 8) & 0x0FF), (byte) (timeout_ms & 0x0FF), retryCount));
        return writeToData(listen, receiveDelay);
    }


    public RFSpyResponse transmitThenReceive(RadioPacket pkt, int timeout_ms) {
        return transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, timeout_ms, (byte) 0);
    }


    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms, byte listenChannel, int timeout_ms, byte retryCount) {

        int sendDelay = repeatCount * delay_ms;
        int receiveDelay = timeout_ms * (retryCount + 1);
        byte[] sendAndListen = getCommandArray(RFSpyCommand.SendAndListen, getByteArray(sendChannel, repeatCount, delay_ms, listenChannel, (byte) ((timeout_ms >> 24) & 0x0FF), (byte) ((timeout_ms >> 16) & 0x0FF), (byte) ((timeout_ms >> 8) & 0x0FF), (byte) (timeout_ms & 0x0FF), (byte) retryCount));
        byte[] fullPacket = ByteUtil.concat(sendAndListen, pkt.getEncoded());
        return writeToData(fullPacket, sendDelay + receiveDelay + EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
    }


    public RFSpyResponse updateRegister(CC111XRegister reg, int val) {
        byte[] updateRegisterPkt = getCommandArray(RFSpyCommand.UpdateRegister, getByteArray(reg.value, (byte) val));
        RFSpyResponse resp = writeToData(updateRegisterPkt, EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
        return resp;
    }


    public void setBaseFrequency(double freqMHz) {
        int value = (int) (freqMHz * 1000000 / ((double) (RILEYLINK_FREQ_XTAL) / Math.pow(2.0, 16.0)));
        updateRegister(CC111XRegister.freq0, (byte) (value & 0xff));
        updateRegister(CC111XRegister.freq1, (byte) ((value >> 8) & 0xff));
        updateRegister(CC111XRegister.freq2, (byte) ((value >> 16) & 0xff));
        LOG.warn("Set frequency to {}", freqMHz);

        configureRadioForRegion(RileyLinkUtil.getRileyLinkTargetFrequency());
    }


    private byte[] getByteArray(byte... input) {
        return input;
    }


    private byte[] getCommandArray(RFSpyCommand command, byte[] body) {
        int bodyLength = body == null ? 0 : body.length;

        byte[] output = new byte[bodyLength + 1];

        output[0] = command.code;

        if (body != null) {
            for(int i = 0; i < body.length; i++) {
                output[i + 1] = body[i];
            }
        }

        return output;
    }


    private void configureRadioForRegion(RileyLinkTargetFrequency frequency) {

        // we update registers only on first run, or if region changed
        if (selectedTargetFrequency == frequency)
            return;

        switch (frequency) {
            case Medtronic_WorldWide: {
                //updateRegister(CC111X_MDMCFG4, (byte) 0x59);
                setRXFilterMode(RXFilterMode.Wide);
                //updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                //updateRegister(CC111X_MDMCFG2, (byte) 0x33);
                updateRegister(CC111XRegister.mdmcfg1, 0x62);
                updateRegister(CC111XRegister.mdmcfg0, 0x1A);
                updateRegister(CC111XRegister.deviatn, 0x13);
            }
            break;

            case Medtronic_US: {
                //updateRegister(CC111X_MDMCFG4, (byte) 0x99);
                setRXFilterMode(RXFilterMode.Narrow);
                //updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                //updateRegister(CC111X_MDMCFG2, (byte) 0x33);
                updateRegister(CC111XRegister.mdmcfg1, 0x61);
                updateRegister(CC111XRegister.mdmcfg0, 0x7E);
                updateRegister(CC111XRegister.deviatn, 0x15);

            }
            break;

            case Omnipod: {
                LOG.debug("No region configuration for RfSpy and {}", frequency.name());
            }

            default:
                // no configuration
                break;

        }

        this.selectedTargetFrequency = frequency;
    }


    private void setRXFilterMode(RXFilterMode mode) {

        byte drate_e = (byte) 0x9;  // exponent of symbol rate (16kbps)
        byte chanbw = mode.value;

        updateRegister(CC111XRegister.mdmcfg4, (byte) (chanbw | drate_e));
    }


    @Deprecated
    private int determineRegion(double freqMHz) {
        int region = 0; // 1 - MDT Worldwide, 2 - MDT US, 3 - Omnipod, 0 - Undefined

        if ((freqMHz >= 916.45) && (freqMHz <= 916.80)) {
            region = 1;
        } else if ((freqMHz >= 868.25) && (freqMHz <= 868.65)) {
            region = 2;
        } else if ((freqMHz >= 443.45) && (freqMHz <= 444.25)) { // 433.92 (exact frequencies will be added later

        } else {
            region = 0;
        }

        LOG.debug("Determine Region: " + region);

        return region;
    }


}
