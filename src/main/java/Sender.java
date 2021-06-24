import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;


public class Sender extends Host implements Runnable {

    private String receiverIp;
    private final int receiverPort;
    private final String filename; // 要发送的文件名

    private final int MSS; //最大报文长度
    private final int MWS; //最大发送窗口，我们设置的是MSS的50倍
    private final int MSL = 50; // 最长报文生存时间

    private volatile byte[] outBuffer; // 发送缓存

    private volatile byte[] inBuffer; // 收到的DataGram缓存

    private final long resendDelay = 1000;

    private final int maxSendTimes = 3;

    private volatile Map<StpPacket, Integer> packetCache; //已发送但未收到确认的数据包缓存 key为数据包，value为发送次数

    private int seq = 0; //记录下次发送新报文的seq（每次发送新报文时应更新此变量）

    private volatile Timer timer; // 计时器，用来处理超时重传

    private final double PDrop; //丢包率 手动设置

    private volatile SenderState state = SenderState.CLOSED;

    private final DatagramSocket socket;

    public Sender(String receiverIp, int receiverPort, String filename, int MSS, double PDrop) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.MWS = 50 * MSS;

        this.receiverPort = receiverPort;
        this.PDrop = PDrop;
        this.receiverIp = receiverIp;
        if (receiverIp.equals("127.0.0.1") || receiverIp.toLowerCase().equals("localhost")) {
            this.receiverIp = getLocalIPV4Address().getHostAddress();
        }
        String senderIp = getLocalIPV4Address().getHostAddress();
        System.out.println(senderIp);
        int senderPort = 8080;
        this.socket = new DatagramSocket(senderPort, InetAddress.getByName(senderIp));
        this.packetCache = new HashMap<>();
        timer = new Timer(true);
        inBuffer = new byte[13 + MSS]; // 13是header的长度
        this.outBuffer = new byte[MSS];
    }

    /**
     * @param args 一共五个参数
     * [0] receiver IP Host
     * [1] receiver port
     * [2] file to send
     * [3] MSS
     * [4] PDrop
     */
    public static void main(String[] args) {
        //
        Sender sender = null;
        try {
            sender = new Sender(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), Double.parseDouble(args[4]));
        } catch (SocketException e) {
            System.out.println("Sender建立失败");
            return;
        } catch (UnknownHostException e) {
            System.out.println("无效的IP地址");
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("参数数量不符合要求");
            return;
        } catch (NumberFormatException e) {
            System.out.println("参数格式不符合要求");
            return;
        }
        System.out.println("Sender初始化成功");
        sender.start();

    }

    /**
     * 检查程序运行状态，控制程序运行，结束
     */
    public void start() {
        Thread t = new Thread(this);
        t.start();
        if (!establishConnection()) {
            System.out.println("建立连接失败，程序结束");
            this.state = SenderState.FIN_CLOSED;
            System.exit(0);
            return;
        }
        System.out.println("建立连接成功，开始传输数据");
        if (!transport()) {
            System.out.println("传输数据失败，程序结束");
            this.state = SenderState.FIN_CLOSED;
            System.exit(0);
            return;
        }
        System.out.println("传输数据成功，开始释放连接");
        if (!killConnection()) {
            System.out.println("释放连接失败，程序结束");
            this.state = SenderState.FIN_CLOSED;
            System.exit(0);
            return;
        }
        try {
            System.out.println("等待2MSL");
            Thread.sleep(2*MSL);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("释放连接成功，程序结束");
        System.exit(0);
    }

    /**
     * 接收报文(线程)
     * 持续监听端口，接受响应报文，并对响应做出处理
     */
    @Override
    public void run() {
        while (state != SenderState.FIN_CLOSED) {
            DatagramPacket datagramPacket = new DatagramPacket(inBuffer, inBuffer.length);
            try {
                socket.receive(datagramPacket);
            } catch (IOException e) {
                System.out.println("接收错误");
            }
            StpPacket stpPacket = new StpPacket(inBuffer);
            handleReceivePacket(stpPacket);
        }
        try {
            Thread.sleep(2*MSL);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("结束接收监听");
    }

    private synchronized void handleReceivePacket(StpPacket stpPacket) {

        switch (state) {
            case SYN_SENT:
                if (stpPacket.isSYN()) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null && packetInCache.isSYN()) {
                        System.out.println("收到握手响应");
                        this.state = SenderState.ESTABLISHED;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case ESTABLISHED:
                if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null) {
                        System.out.println("ack------" + " time:" + new Date().toString() + " isFIN:" + stpPacket.isFIN() + " isSYN:" + stpPacket.isSYN() + " ack:" + stpPacket.getAck() + " seq:" + stpPacket.getSeq());
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case FIN_WAIT:
                if (stpPacket.isFIN()) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null && packetInCache.isFIN()) {
                        System.out.println("ack------" + " time:" + new Date().toString() + " isFIN:" + stpPacket.isFIN() + " isSYN:" + stpPacket.isSYN() + " ack:" + stpPacket.getAck() + " seq:" + stpPacket.getSeq());
                        try {
                            send(false,false, seq,stpPacket.getSeq()+1, null);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep(2*MSL);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        this.state = SenderState.FIN_CLOSED;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
        }
    }

    /**
     * 通过报文对应的确认报文的ack在Cache中寻找原报文
     *
     * @param ack 确认号
     * @return 目标报文
     */
    private StpPacket findPacketFromCacheByAck(int ack) {
        for (StpPacket stpPacket : packetCache.keySet()) {
            int seqInNeed;
            if (stpPacket.getData() == null || stpPacket.getData().length == 0) {
                seqInNeed = ack - 1;
            } else {
                seqInNeed = ack - stpPacket.getData().length;
            }
            if (stpPacket.getSeq() == seqInNeed) {
                return stpPacket;
            }
        }
        return null;
    }

    /**
     * 建立连接 发送握手请求
     * @return 握手成功与否
     */
    private boolean establishConnection() {
        return handShake(true);
    }

    /**
     * 断开连接，发送握手
     * @return 握手成功与否
     */
    private boolean killConnection() {
        return handShake(false);
    }

    /**
     * 对握手的封装
     * @param isSYN 同步位是否为1，为1是建立连接的握手。否则为释放连接的握手
     * @return 成功返回true
     */
    private boolean handShake(boolean isSYN) {
        try {
            send(isSYN, !isSYN, seq, 0, null);
            this.state = isSYN ? SenderState.SYN_SENT : SenderState.FIN_WAIT;
        } catch (IOException e) {
            System.out.println("发送握手报文失败");
            return false;
        }

        long startTime = System.currentTimeMillis();
        long endTime = startTime + resendDelay * maxSendTimes;
        while (state != (isSYN ? SenderState.ESTABLISHED : SenderState.FIN_CLOSED)) {
            //一直等待到握手成功
            if (System.currentTimeMillis() > endTime) {
                System.out.println("握手超时");
                return false;
            }
        }
        return true;
    }

    /**
     * 将文件内容写入缓存
     * 并按照窗口发送数据报
     * @return 成功返回true，否则返回false
     */
    private boolean transport() {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            System.out.println("发送文件名找不到");
            return false;
        }
        boolean isFileFinish = false;

        long latestTime = System.currentTimeMillis() + resendDelay * maxSendTimes * MWS / MSS;
        //发送窗口中的所有报文都重传所需要的时间

        while (!(isFileFinish && packetCache.size() == 0)) {
            if (state != SenderState.ESTABLISHED) {
                return false; // 首先保证连接建立了
            }
            if (packetCache.size() != 0 && System.currentTimeMillis() > latestTime) {
                System.out.println("等待确认报文超时");
                return false;
            }
            if (packetCache.size() != 0) {
                //如果缓存不为空，即窗口内仍未均收到
                continue;
            }
            // 前面的都确认了，开始继续发送
            for (int i = 0; i < MWS / MSS; i++) {
                int bufferLen = -1;
                try {
                    bufferLen = fileInputStream.read(outBuffer);
                } catch (IOException e) {
                    System.out.println("读取待发送文件失败");
                    return false;
                }
                if (bufferLen == -1) {
                    isFileFinish = true;
                    break;
                }
                byte[] data;
                if (bufferLen == outBuffer.length) {
                    data = outBuffer;
                } else {
                    data = new byte[bufferLen]; //读取的内容不一定会把buffer填满，所以在这里作处理，去掉多余部分
                    System.arraycopy(outBuffer, 0, data, 0, bufferLen);
                }
                try {
                    this.send(false, false, seq, seq + MSS, data);
                } catch (IOException e) {
                    System.out.println("发送数据失败");
                    return false;
                }

            }
        }

        try {
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("文件读入流关闭失败");
        }

        return true;
    }

    /**
     * 报文发送封装（发送未发送过的新数据报）
     * 有一定概率发生丢包，不发送此报文
     */
    private synchronized void send(boolean isSYN, boolean isFIN, int seq, int ack, byte[] data) throws IOException {
        StpPacket stpPacket = new StpPacket(isSYN, isFIN, seq, ack, MSS, data);

        double random = Math.random();
        if (random > PDrop || data == null) {
            System.out.println("send------" + " time:" + new Date().toString() + " isFIN:" + stpPacket.isFIN() + " isSYN:" + stpPacket.isSYN() + " ack:" + stpPacket.getAck() + " seq:" + stpPacket.getSeq());
            socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        } else {
            System.out.println("丢包序号：" + stpPacket.getSeq());
        }

        if (data == null || data.length == 0) this.seq++;
        else this.seq += data.length;
        packetCache.put(stpPacket, 1);
        timeoutResend(stpPacket);
    }

    /**
     * 报文发送封装（重发已发送过的数据报）
     */
    private synchronized void resend(StpPacket stpPacket) throws IOException {
        double random = Math.random();
        if (random > PDrop || stpPacket.getData() == (null)) {
            System.out.println("成功重传序号：" + stpPacket.getSeq());
            socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        } else {
            System.out.println("重传丢包序号：" + stpPacket.getSeq());
        }
        packetCache.put(stpPacket, packetCache.get(stpPacket) + 1);
        timeoutResend(stpPacket);
    }

    /**
     * 设置定时重传，如果已经确认了不会再重发
     * @param stpPacket 重传的STP报文
     */
    private void timeoutResend(final StpPacket stpPacket) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public synchronized void run() {
                //如果缓存区还有此数据包，即还未收到确认，才会重发
                if (packetCache.containsKey(stpPacket) && packetCache.get(stpPacket) != maxSendTimes && state != SenderState.FIN_WAIT) {
                    try {
                        resend(stpPacket);
                    } catch (IOException ie) {
                        ie.printStackTrace();
                    }
                } else if (packetCache.containsKey(stpPacket) && packetCache.get(stpPacket) == maxSendTimes && state != SenderState.FIN_WAIT) {

                    packetCache.remove(stpPacket);
                    try {
                        System.out.println("重发失败，停止传输" + stpPacket.getSeq()); //多次失败，断开连接
                        send(false, true, stpPacket.getSeq(), 0, null);
                        state = SenderState.FIN_WAIT;
                        timer.cancel();
                    } catch (IOException e) {
                        System.out.println("发送握手报文失败");
                    }

                }
            }
        }, resendDelay);
    }
}