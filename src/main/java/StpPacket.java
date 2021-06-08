//报文类

public class StpPacket {

    /**
     * header固定长度为13字节
     * [0]:包含标志位 第一位为SYN 第二位是FIN 剩余六位为空
     * [1]~[4] : 32位的seq
     * [5]~[8]: 32位的ack
     * [9]~[12]: 32位的MSS
     */
    private byte[] header = new byte[13];

    /**
     * data部分长度不一
     * 最大长度为MSS
     */
    private byte[] data;

    /**
     * 发送报文时 报文的构造函数
     *
     * @param isSYN
     * @param isFIN
     * @param seq
     * @param ack
     * @param data
     */
    public StpPacket(boolean isSYN, boolean isFIN, int seq, int ack, int MSS, byte[] data) {
        /**
         * 10000000  -128
         * 11000000
         * 01000000  64
         * 00000000
         */
        header = new byte[13];
        byte sign = 0;
        if (isSYN) {
            sign |= 0b10000000;
        }
        if (isFIN) {
            sign |= 0b01000000;
        }
        header[0] = sign;
        byte[] seqBytes = intToByte4(seq);
        byte[] ackBytes = intToByte4(ack);
        byte[] mssBytes = intToByte4(MSS);
        for (int i = 0; i < 4; i++) {
            header[i + 1] = seqBytes[i];
            header[i + 5] = ackBytes[i];
            header[i + 9] = mssBytes[i];
        }
        if (data != null) {
            this.data = data.clone();
        }
    }

    /**
     * 接收报文时，根据字节数组生成报文的构造函数
     *
     * @param buffer
     */
    public StpPacket(byte[] buffer) {
        if (buffer == null || buffer.length == 0) System.out.println("丢包！");
        assert buffer != null;
        System.arraycopy(buffer, 0, header, 0, 13);
        data = new byte[buffer.length - 13];
        System.arraycopy(buffer, 13, data, 0, data.length);
    }


    /**
     * 序列化为字节数组 以发送
     *
     * @return
     */
    public byte[] toByteArray() {
        int len;//报文长度

        //整合header和data，data可能为空
        if (data != null) {
            len = header.length + data.length;
        } else {
            len = header.length;
        }

        byte[] res = new byte[len];

        //拷贝header
        System.arraycopy(header, 0, res, 0, 13);

        //拷贝data

        if (data!= null) System.arraycopy(data, 0, res, 13, len - 13);

        return res;
    }

    /**
     * 获取data数组
     */
    public byte[] getData() {
        if(data==null) return null;
        return this.data.clone();
    }

    /**
     * 判断是否同步
     */
    public boolean isSYN() {
        return (header[0] & 0b10000000) == 0b10000000;
    }

    /**
     * 判断是否是结束报文
     */
    public boolean isFIN() {
        return (header[0] & 0b01000000) == 0b01000000;
    }


    public int getSeq() {
        StringBuilder str = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            str.append(byteToString(header[i]));
        }
        return Integer.parseInt(str.toString(), 2);
    }

    public int getAck() {
        StringBuilder str = new StringBuilder();
        for (int i = 5; i <= 8; i++) {
            str.append(byteToString(header[i]));
        }
        return Integer.parseInt(str.toString(), 2);
    }

    public int getMSS() {
        StringBuilder str = new StringBuilder();
        for (int i = 9; i <= 12; i++) {
            str.append(byteToString(header[i]));
        }
        return Integer.parseInt(str.toString(), 2);
    }

    private byte[] intToByte4(int i) {
        return new byte[] {
                // 每次移位后只保留低八位的二进制串
                (byte) ((i >> 24) & 0xFF),
                (byte) ((i >> 16) & 0xFF),
                (byte) ((i >> 8) & 0xFF),
                (byte) (i & 0xFF)
        };
    }

    private String byteToString(byte n) {
        return Integer.toBinaryString((n & 0xFF) + 0x100).substring(1);
    }
}

