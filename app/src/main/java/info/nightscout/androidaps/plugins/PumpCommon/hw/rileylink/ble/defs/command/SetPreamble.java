package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.command;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;

public class SetPreamble extends RileyLinkCommand {

    private int preamble;

    public SetPreamble(RileyLinkFirmwareVersion version, int preamble) throws Exception {
        super(version);
        this.type = RileyLinkCommandType.SetPreamble;
        if (preamble < 0 || preamble > 0xFFFF) {
            throw new Exception("preamble value is out of range");
        }
        this.preamble = preamble;
    }

    @Override
    public byte[] getRaw() {
        byte[] bytes = ByteBuffer.allocate(4).putInt(preamble).array();
        return getByteArray(this.type.code, bytes[2], bytes[3]);
    }
}
