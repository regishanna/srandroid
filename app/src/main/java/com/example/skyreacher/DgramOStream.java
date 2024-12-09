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

        // send the header
        // => buffer size in big endian
        int buf_length = buf.length;
        byte[] header = new byte[] {
                (byte)((buf_length >> 8) & 0xff),
                (byte)(buf_length        & 0xff),
        };
        output_stream.write(header);

        // send the buffer
        output_stream.write(buf);
    }

    public byte[] recv(Socket sock) throws Exception
    {
        byte[] returned_dgram = null;

        try
        {
            InputStream input_stream = sock.getInputStream();

            // should we receive the header or the buffer?
            if (m_rx_header_cur_len < m_rx_header.length)
            {
                // we have not completely received the header, we continue
                int len = input_stream.read(m_rx_header, m_rx_header_cur_len, m_rx_header.length - m_rx_header_cur_len);
                if (len < 0)
                    throw new Exception("Closed socket");
                m_rx_header_cur_len += len;

                if (m_rx_header_cur_len == m_rx_header.length)
                {
                    // end of reception of the header, we check that the size of the buffer is compatible
                    m_dgram_len = (m_rx_header[0] << 8) + m_rx_header[1];
                    if (m_dgram_len > m_rx_buf_max_len)
                        throw new Exception("Receive buffer too small");
                }
            }
            else
            {
                // we have already received the header, we receive the buffer (or we continue to receive it)
                int len = input_stream.read(m_rx_buf, m_rx_buf_cur_len, m_dgram_len - m_rx_buf_cur_len);
                if (len < 0)
                    throw new Exception("Closed socket");
                m_rx_buf_cur_len += len;

                if (m_rx_buf_cur_len == m_dgram_len)
                {
                    // end of datagram reception
                    returned_dgram = Arrays.copyOfRange(m_rx_buf, 0, m_dgram_len);
                    clear_rx_buf();
                }
            }
        }
        catch (IOException e)
        {
            // in the event of an error, we re-initialize the pointers of the reception buffer
            clear_rx_buf();
            throw new Exception("Receive error");
        }

        return returned_dgram;
    }
}
