package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Lcd;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Empaqueta una Lcd (paleta + píxeles a 4 bits comprimidos + textos) para mandarla por red. */
public final class Trama {
    private Trama() {}

    public static byte[] codificar(Lcd l) {
        byte[] raw = new byte[Lcd.W * Lcd.H / 2];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) ((l.buf[2 * i] & 15) | ((l.buf[2 * i + 1] & 15) << 4));
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(2048);
        byte[] tmp = new byte[4096];
        while (!d.finished()) o.write(tmp, 0, d.deflate(tmp));
        d.end();
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeVarInt(l.paleta.length);
        for (int c : l.paleta) b.writeInt(c);
        b.writeByteArray(o.toByteArray());
        int n = Math.min(40, l.textos.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Object[] t = l.textos.get(i);
            String s = (String) t[0];
            b.writeUtf(s.length() > 40 ? s.substring(0, 40) : s, 160);
            b.writeShort((int) t[1]);
            b.writeShort((int) t[2]);
            b.writeByte((int) t[3]);
        }
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        b.release();
        return out;
    }

    public static boolean decodificar(byte[] datos, Lcd l) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(datos));
        try {
            int np = Math.min(16, b.readVarInt());
            int[] pal = new int[np];
            for (int i = 0; i < np; i++) pal[i] = b.readInt();
            byte[] comp = b.readByteArray(30000);
            Inflater inf = new Inflater();
            inf.setInput(comp);
            byte[] raw = new byte[Lcd.W * Lcd.H / 2];
            int k = 0;
            while (k < raw.length && !inf.finished()) {
                int r = inf.inflate(raw, k, raw.length - k);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                k += r;
            }
            inf.end();
            for (int i = 0; i < raw.length; i++) {
                l.buf[2 * i] = (byte) (raw[i] & 15);
                l.buf[2 * i + 1] = (byte) ((raw[i] >> 4) & 15);
            }
            l.paleta = pal.length > 0 ? pal : Lcd.VERDE;
            l.textos.clear();
            int n = Math.min(40, b.readVarInt());
            for (int i = 0; i < n; i++) l.textos.add(new Object[]{b.readUtf(160), (int) b.readShort(), (int) b.readShort(), (int) b.readByte()});
            return true;
        } catch (DataFormatException | RuntimeException e) {
            return false;
        } finally {
            b.release();
        }
    }
}
