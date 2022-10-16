package com.example.skyreacher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public class DgramOStream {

    byte[] m_rx_buf;
    final int m_rx_buf_max_len;
    int m_rx_buf_cur_len;

    byte[] m_rx_header;
    int m_rx_header_cur_len;

    int m_dgram_len;


    DgramOStream(int rx_buf_len)
    {
        m_rx_buf = new byte[rx_buf_len];
        m_rx_buf_max_len = rx_buf_len;

        m_rx_header = new byte[2];

        clear_rx_buf();
    }

    private void clear_rx_buf()
    {
        m_rx_buf_cur_len = 0;
        m_rx_header_cur_len = 0;
        m_dgram_len = 0;
    }

    public static void send(Socket sock, byte[] buf) throws IOException
    {
        OutputStream output_stream = sock.getOutputStream();

        // envoi de l'entete
        // => taille du buffer en big endian
        int buf_length = buf.length;
        byte[] header = new byte[] {
                (byte)((buf_length >> 8) & 0xff),
                (byte)(buf_length        & 0xff),
        };
        output_stream.write(header);

        // envoi du buffer
        output_stream.write(buf);
    }

    public byte[] recv(Socket sock) throws Exception
    {
        byte[] returned_dgram = null;

        try
        {
            InputStream input_stream = sock.getInputStream();

            // doit-on recevoir le header ou le buffer ?
            if (m_rx_header_cur_len < m_rx_header.length)
            {
                // on n'a pas totalement recu le header, on continue
                int len = input_stream.read(m_rx_header, m_rx_header_cur_len, m_rx_header.length - m_rx_header_cur_len);
                if (len < 0)
                    throw new Exception("Socket ferme");
                m_rx_header_cur_len += len;

                if (m_rx_header_cur_len == m_rx_header.length)
                {
                    // fin de reception du header, on verifie que la taille du buffer est compatible
                    m_dgram_len = (m_rx_header[0] << 8) + m_rx_header[1];
                    if (m_dgram_len > m_rx_buf_max_len)
                        throw new Exception("Buffer de reception trop petit");
                }
            }
            else
            {
                // on a deja recu le header, on recoit le buffer (ou on continue de le recevoir)
                int len = input_stream.read(m_rx_buf, m_rx_buf_cur_len, m_dgram_len - m_rx_buf_cur_len);
                if (len < 0)
                    throw new Exception("Socket ferme");
                m_rx_buf_cur_len += len;

                if (m_rx_buf_cur_len == m_dgram_len)
                {
                    // fin de reception du datagram
                    returned_dgram = Arrays.copyOfRange(m_rx_buf, 0, m_dgram_len);
                    clear_rx_buf();
                }
            }
        }
        catch (IOException e)
        {
            // en cas d'erreur, on re-initialise les pointeurs du buffer de reception
            clear_rx_buf();
            throw new Exception("Erreur de reception");
        }

        return returned_dgram;
    }
}
