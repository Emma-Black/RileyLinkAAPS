package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RFTools;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RFSpyCommand;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.CRC;

/**
 * Created by geoff on 5/30/16.
 */
public class RadioResponse {

    private static final Logger LOG = LoggerFactory.getLogger(RadioResponse.class);

    public boolean decodedOK = false;
    public int rssi;
    public int responseNumber;
    public byte[] decodedPayload = new byte[0];
    public byte receivedCRC;
    private RFSpyCommand command;


    public RadioResponse() {
    }


    public RadioResponse(byte[] rxData) {
        init(rxData);
    }

    public RadioResponse(RFSpyCommand command, byte[] raw) {

        this.command = command;
        init(raw);
    }


    public boolean isValid() {

        if (command!=null && !command.isEncoded())
        {
            return true;
        }


        if (!decodedOK) {
            return false;
        }
        if (decodedPayload != null) {
            if (receivedCRC == CRC.crc8(decodedPayload)) {
                return true;
            }
        }
        return false;
    }


    public void init(byte[] rxData) {
        if (rxData == null) {
            return;
        }
        if (rxData.length < 3) {
            // This does not look like something valid heard from a RileyLink device
            return;
        }
        rssi = rxData[0];
        responseNumber = rxData[1];
        byte[] encodedPayload = ByteUtil.substring(rxData, 2, rxData.length - 2);
        try {

            boolean isEncoded = command==null || command.isEncoded();

            if (isEncoded) {
                byte[] decodeThis = RFTools.decode4b6b(encodedPayload);
                decodedOK = true;
                decodedPayload = ByteUtil.substring(decodeThis, 0, decodeThis.length - 1);
                byte calculatedCRC = CRC.crc8(decodedPayload);
                receivedCRC = decodeThis[decodeThis.length - 1];
                if (receivedCRC != calculatedCRC) {
                    LOG.error(String.format("RadioResponse: CRC mismatch, calculated 0x%02x, received 0x%02x", calculatedCRC, receivedCRC));
                }
            }
            else {
                decodedOK = true;
                decodedPayload = encodedPayload;
            }

            //byte[] decodeThis = RFTools.decode4b6b(encodedPayload);
        } catch (NumberFormatException e) {
            decodedOK = false;
            LOG.error("Failed to decode radio data: " + ByteUtil.shortHexString(encodedPayload));
        }
    }


    public void init(byte[] rxData, boolean isEncoded) {
        if (rxData == null) {
            return;
        }
        if (rxData.length < 3) {
            // This does not look like something valid heard from a RileyLink device
            return;
        }
        rssi = rxData[0];
        responseNumber = rxData[1];
        byte[] encodedPayload = ByteUtil.substring(rxData, 2, rxData.length - 2);
        try {
            byte[] decodeThis = RFTools.decode4b6b(encodedPayload);
            decodedOK = true;
            decodedPayload = ByteUtil.substring(decodeThis, 0, decodeThis.length - 1);
            byte calculatedCRC = CRC.crc8(decodedPayload);
            receivedCRC = decodeThis[decodeThis.length - 1];
            if (receivedCRC != calculatedCRC) {
                LOG.error("RadioResponse: CRC mismatch, calculated 0x%02x, received 0x%02x", calculatedCRC, receivedCRC);
            }
        } catch (NumberFormatException e) {
            decodedOK = false;
            LOG.error("Failed to decode radio data: " + ByteUtil.shortHexString(encodedPayload));
        }
    }


    public byte[] getPayload() {
        return decodedPayload;
    }
}
