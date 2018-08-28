package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data;

import org.apache.commons.lang3.NotImplementedException;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RFTools;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.CRC;

/**
 * Created by geoff on 5/22/16.
 */

public class RadioPacket {
    private final RileyLinkFirmwareVersion version;
    protected byte[] pkt;

    public RadioPacket(byte[] pkt, RileyLinkFirmwareVersion version) {
        this.pkt = pkt;
        this.version = version;
    }


    public byte[] getRaw() {
        return pkt;
    }

    public byte[] getWithCRC() {
        byte[] withCRC = ByteUtil.concat(pkt, CRC.crc8(pkt));
        return withCRC;
    }

    public byte[] getEncoded() {

        switch (RileyLinkUtil.getEncoding()) {
            case Manchester: {  //We have this encoding in RL firmware
                return pkt;
            }

            case FourByteSixByte: {
                byte[] withCRC = getWithCRC();

                byte[] encoded = RFTools.encode4b6b(withCRC);
                return ByteUtil.concat(encoded, (byte) 0);
            }

            default:
                throw new NotImplementedException(("Encoding not supported: " + RileyLinkUtil.getEncoding().toString()));
        }
    }

}
