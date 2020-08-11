package com.sunggil.blesample.network;

import android.os.Build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public class CustomSSLOkHttpClient {
    private static CustomSSLOkHttpClient customSSLOkHttpClient = new CustomSSLOkHttpClient();

    public static CustomSSLOkHttpClient getInstance() {
        if (customSSLOkHttpClient == null) {
            customSSLOkHttpClient = new CustomSSLOkHttpClient();
        }

        return customSSLOkHttpClient;
    }

    public OkHttpClient getSSLOkHttpClient() {
        OkHttpClient.Builder client = new OkHttpClient()
                .newBuilder()
                .followRedirects(true)
                .followSslRedirects(true);

        return enableTls12OnPreLollipop(client).build();
    }

    //원본
//    private OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
//        if (16 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < 22) {
//            try {
//                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
//                        TrustManagerFactory.getDefaultAlgorithm());
//                trustManagerFactory.init((KeyStore) null);
//                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
//                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
//                    throw new IllegalStateException("Unexpected default trust managers:"
//                            + Arrays.toString(trustManagers));
//                }
//                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
//
//                SSLContext sc = SSLContext.getInstance("TLSv1.2");
////                SSLContext sc = SSLContext.getInstance("SSL");
//                sc.init(null, new TrustManager[] { trustManager }, null);
//                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()), trustManager);
//
//                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
//                        .tlsVersions(TlsVersion.TLS_1_2)
//                        .build();
//
//                List<ConnectionSpec> specs = new ArrayList<>();
//                specs.add(cs);
//                specs.add(ConnectionSpec.COMPATIBLE_TLS);
//                specs.add(ConnectionSpec.CLEARTEXT);
//
//                client.connectionSpecs(specs);
//            } catch (Exception exc) {
//                Log.e("SG2", "AAAAA Error while setting TLS 1.2", exc);
//            }
//        }
//
//        return client;
//    }

    private OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        if (16 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < 22) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
                }
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { trustManager }, null);


                SSLContext sc = SSLContext.getInstance("TLSv1.2");
//                sc.init(null, new TrustManager[] { trustManager }, null);
                sc.init(null, null, null);
                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()), trustManager);

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            } catch (Exception exc) {
            }
        }

        return client;
    }

    public Tls12SocketFactory getTTLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        return new Tls12SocketFactory();
    }

    class Tls12SocketFactory extends SSLSocketFactory {
        private final String[] TLS_V12_ONLY = {"TLSv1.2"};

        final SSLSocketFactory delegate;

        public Tls12SocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            delegate = context.getSocketFactory();
        }

        public Tls12SocketFactory(SSLSocketFactory base) {
            this.delegate = base;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort));
        }

        private Socket patch(Socket s) {
            if (s instanceof SSLSocket) {
                ((SSLSocket) s).setEnabledProtocols(TLS_V12_ONLY);
            }
            return s;
        }
    }
}