package com.miscelanea.client.arcade;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Empaqueta una Pantalla (índices de 6 bits comprimidos + textos) para retransmitirla a quien mira. */
public final class Trama {
    private Trama() {}

    public static byte[] codificar(Pantalla p) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(p.buf);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(8192);
        byte[] tmp = new byte[8192];
        while (!d.finished()) o.write(tmp, 0, d.deflate(tmp));
        d.end();
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeByteArray(o.toByteArray());
        int n = Math.min(48, p.textos.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Object[] t = p.textos.get(i);
            String s = (String) t[0];
            b.writeUtf(s.length() > 48 ? s.substring(0, 48) : s, 200);
            b.writeShort((int) t[1]);
            b.writeShort((int) t[2]);
            b.writeByte((int) t[3]);
        }
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        b.release();
        return out;
    }

    public static boolean decodificar(byte[] datos, Pantalla p) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(datos));
        try {
            byte[] comp = b.readByteArray(200000);
            Inflater inf = new Inflater();
            inf.setInput(comp);
            int k = 0;
            while (k < p.buf.length && !inf.finished()) {
                int r = inf.inflate(p.buf, k, p.buf.length - k);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                k += r;
            }
            inf.end();
            for (int i = 0; i < p.buf.length; i++) p.buf[i] &= 63;
            p.textos.clear();
            int n = Math.min(48, b.readVarInt());
            for (int i = 0; i < n; i++) p.textos.add(new Object[]{b.readUtf(200), (int) b.readShort(), (int) b.readShort(), (int) b.readByte()});
            return true;
        } catch (DataFormatException | RuntimeException e) {
            return false;
        } finally {
            b.release();
        }
    }
}
