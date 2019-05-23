// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;



import java.io.IOException;
import java.io.StringReader;
import java.net.Authenticator.RequestorType;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.Capabilities.CapabilitiesParser;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Class that encapsulates the communications with the <a href="http://wiki.openstreetmap.org/wiki/API_v0.6">OSM API</a>.<br><br>
 *
 * All interaction with the server-side OSM API should go through this class.<br><br>
 *
 * It is conceivable to extract this into an interface later and create various
 * classes implementing the interface, to be able to talk to various kinds of servers.
 * @since 1523
 */
public class OsmApi extends OsmConnection {

    /**
     * Maximum number of retries to send a request in case of HTTP 500 errors or timeouts
     */
    public static final int DEFAULT_MAX_NUM_RETRIES = 5;

    /**
     * Maximum number of concurrent download threads, imposed by
     * <a href="http://wiki.openstreetmap.org/wiki/API_usage_policy#Technical_Usage_Requirements">
     * OSM API usage policy.</a>
     * @since 5386
     */
    public static final int MAX_DOWNLOAD_THREADS = 2;

    // The collection of instantiated OSM APIs
    private static final Map<String, OsmApi> instances = new HashMap<>();

    private static final ListenerList<OsmApiInitializationListener> listeners = ListenerList.create();

    private URL url;

    /**
     * OSM API initialization listener.
     * @since 12804
     */
    public interface OsmApiInitializationListener {
        /**
         * Called when an OSM API instance has been successfully initialized.
         * @param instance the initialized OSM API instance
         */
        void apiInitialized(OsmApi instance);
    }

    /**
     * Adds a new OSM API initialization listener.
     * @param listener OSM API initialization listener to add
     * @since 12804
     */
    public static void addOsmApiInitializationListener(OsmApiInitializationListener listener) {
        listeners.addListener(listener);
    }

    /**
     * Removes an OSM API initialization listener.
     * @param listener OSM API initialization listener to remove
     * @since 12804
     */
    public static void removeOsmApiInitializationListener(OsmApiInitializationListener listener) {
        listeners.removeListener(listener);
    }

    /**
     * Replies the {@link OsmApi} for a given server URL
     *
     * @param serverUrl  the server URL
     * @return the OsmApi
     * @throws IllegalArgumentException if serverUrl is null
     *
     */
    public static OsmApi getOsmApi(String serverUrl) {
        OsmApi api = instances.get(serverUrl);
        if (api == null) {
            api = new OsmApi(serverUrl);
            cacheInstance(api);
        }
        return api;
    }

    protected static void cacheInstance(OsmApi api) {
        instances.put(api.getServerUrl(), api);
    }

    private static String getServerUrlFromPref() {
        return Config.getPref().get("osm-server.url", Config.getUrls().getDefaultOsmApiUrl());
    }

    /**
     * Replies the {@link OsmApi} for the URL given by the preference <code>osm-server.url</code>
     *
     * @return the OsmApi
     */
    public static OsmApi getOsmApi() {
        return getOsmApi(getServerUrlFromPref());
    }

    /** Server URL */
    private final String serverUrl;

    /** API version used for server communications */
    private String version;

    /** API capabilities */
    private Capabilities capabilities;

    /** true if successfully initialized */
    private boolean initialized;

    /**
     * Constructs a new {@code OsmApi} for a specific server URL.
     *
     * @param serverUrl the server URL. Must not be null
     * @throws IllegalArgumentException if serverUrl is null
     */
    protected OsmApi(String serverUrl) {
        CheckParameterUtil.ensureParameterNotNull(serverUrl, "serverUrl");
        this.serverUrl = serverUrl;
    }

    /**
     * Replies the OSM protocol version we use to talk to the server.
     * @return protocol version, or null if not yet negotiated.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Replies the host name of the server URL.
     * @return the host name of the server URL, or null if the server URL is malformed.
     */
    public String getHost() {
        String host = null;
        try {
            host = (new URL(serverUrl)).getHost();
        } catch (MalformedURLException e) {
            Logging.warn(e);
        }
        return host;
    }

    private class CapabilitiesCache extends CacheCustomContent<OsmTransferException> {

        private static final String CAPABILITIES = "capabilities";

        private final ProgressMonitor monitor;
        private final boolean fastFail;

        CapabilitiesCache(ProgressMonitor monitor, boolean fastFail) {
            super(CAPABILITIES + getBaseUrl().hashCode(), CacheCustomContent.INTERVAL_WEEKLY);
            this.monitor = monitor;
            this.fastFail = fastFail;
        }

        @Override
        protected void checkOfflineAccess() {
            OnlineResource.OSM_API.checkOfflineAccess(getBaseUrl(getServerUrlFromPref(), "0.6")+CAPABILITIES, getServerUrlFromPref());
        }

        @Override
        protected byte[] updateData() throws OsmTransferException {
            return sendRequest("GET", CAPABILITIES, null, monitor, false, fastFail).getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Initializes this component by negotiating a protocol version with the server.
     *
     * @param monitor the progress monitor
     * @throws OsmTransferCanceledException If the initialisation has been cancelled by user.
     * @throws OsmApiInitializationException If any other exception occurs. Use getCause() to get the original exception.
     */
    public void initialize(ProgressMonitor monitor) throws OsmTransferCanceledException, OsmApiInitializationException {
        initialize(monitor, false);
    }

    /**
     * Initializes this component by negotiating a protocol version with the server, with the ability to control the timeout.
     *
     * @param monitor the progress monitor
     * @param fastFail true to request quick initialisation with a small timeout (more likely to throw exception)
     * @throws OsmTransferCanceledException If the initialisation has been cancelled by user.
     * @throws OsmApiInitializationException If any other exception occurs. Use getCause() to get the original exception.
     */
    public void initialize(ProgressMonitor monitor, boolean fastFail) throws OsmTransferCanceledException, OsmApiInitializationException {
        if (initialized)
            return;
        cancel = false;
        try {
            CapabilitiesCache cache = new CapabilitiesCache(monitor, fastFail);
            try {
                initializeCapabilities(cache.updateIfRequiredString());
            } catch (SAXParseException parseException) {
                Logging.trace(parseException);
                // XML parsing may fail if JOSM previously stored a corrupted capabilities document (see #8278)
                // In that case, force update and try again
                initializeCapabilities(cache.updateForceString());
            } catch (SecurityException e) {
                Logging.log(Logging.LEVEL_ERROR, "Unable to initialize OSM API", e);
            }
            if (capabilities == null) {
                if (NetworkManager.isOffline(OnlineResource.OSM_API)) {
                    Logging.warn(MessageFormat.format("{0} not available (offline mode)", MessageFormat.format("OSM API")));
                } else {
                    Logging.error(MessageFormat.format("Unable to initialize OSM API."));
                }
                return;
            } else if (!capabilities.supportsVersion("0.6")) {
                Logging.error(MessageFormat.format("This version of JOSM is incompatible with the configured server."));
                Logging.error(MessageFormat.format("It supports protocol version 0.6, while the server says it supports {0} to {1}.",
                        capabilities.get("version", "minimum"), capabilities.get("version", "maximum")));
                return;
            } else {
                version = "0.6";
                initialized = true;
            }

            listeners.fireEvent(l -> l.apiInitialized(this));
        } catch (OsmTransferCanceledException e) {
            throw e;
        } catch (OsmTransferException e) {
            initialized = false;
            NetworkManager.addNetworkError(url, Utils.getRootCause(e));
            throw new OsmApiInitializationException(e);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            initialized = false;
            throw new OsmApiInitializationException(e);
        }
    }

    private synchronized void initializeCapabilities(String xml) throws SAXException, IOException, ParserConfigurationException {
        if (xml != null) {
            capabilities = CapabilitiesParser.parse(new InputSource(new StringReader(xml)));
        }
    }

    private static String getBaseUrl(String serverUrl, String version) {
        StringBuilder rv = new StringBuilder(serverUrl);
        if (version != null) {
            rv.append('/').append(version);
        }
        rv.append('/');
        // this works around a ruby (or lighttpd) bug where two consecutive slashes in
        // an URL will cause a "404 not found" response.
        int p;
        while ((p = rv.indexOf("//", rv.indexOf("://")+2)) > -1) {
            rv.delete(p, p + 1);
        }
        return rv.toString();
    }

    /**
     * Returns the base URL for API requests, including the negotiated version number.
     * @return base URL string
     */
    public String getBaseUrl() {
        return getBaseUrl(serverUrl, version);
    }

    /**
     * Returns the server URL
     * @return the server URL
     * @since 9353
     */
    public String getServerUrl() {
        return serverUrl;
    }

    private void sleepAndListen(int retry, ProgressMonitor monitor) throws OsmTransferCanceledException {
        Logging.info(MessageFormat.format("Waiting 10 seconds ... "));
        for (int i = 0; i < 10; i++) {
            if (monitor != null) {
                monitor.setCustomText(MessageFormat.format("Starting retry {0} of {1} in {2} seconds ...", getMaxRetries() - retry, getMaxRetries(), 10-i));
            }
            if (cancel)
                throw new OsmTransferCanceledException("Operation canceled" + (i > 0 ? " in retry #"+i : ""));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logging.warn("InterruptedException in "+getClass().getSimpleName()+" during sleep");
                Thread.currentThread().interrupt();
            }
        }
        Logging.info(MessageFormat.format("OK - trying again."));
    }

    /**
     * Replies the max. number of retries in case of 5XX errors on the server
     *
     * @return the max number of retries
     */
    protected int getMaxRetries() {
        int ret = Config.getPref().getInt("osm-server.max-num-retries", DEFAULT_MAX_NUM_RETRIES);
        return Math.max(ret, 0);
    }

    /**
     * Determines if JOSM is configured to access OSM API via OAuth
     * @return {@code true} if JOSM is configured to access OSM API via OAuth, {@code false} otherwise
     * @since 6349
     */
    public static boolean isUsingOAuth() {
        return "oauth".equals(getAuthMethod());
    }

    /**
     * Returns the authentication method set in the preferences
     * @return the authentication method
     */
    public static String getAuthMethod() {
        return Config.getPref().get("osm-server.auth-method", "oauth");
    }

    protected final String sendRequest(String requestMethod, String urlSuffix, String requestBody, ProgressMonitor monitor)
            throws OsmTransferException {
        return sendRequest(requestMethod, urlSuffix, requestBody, monitor, true, false);
    }

    /**
     * Generic method for sending requests to the OSM API.
     *
     * This method will automatically re-try any requests that are answered with a 5xx
     * error code, or that resulted in a timeout exception from the TCP layer.
     *
     * @param requestMethod The http method used when talking with the server.
     * @param urlSuffix The suffix to add at the server url, not including the version number,
     *    but including any object ids (e.g. "/way/1234/history").
     * @param requestBody the body of the HTTP request, if any.
     * @param monitor the progress monitor
     * @param doAuthenticate  set to true, if the request sent to the server shall include authentication
     * credentials;
     * @param fastFail true to request a short timeout
     *
     * @return the body of the HTTP response, if and only if the response code was "200 OK".
     * @throws OsmTransferException if the HTTP return code was not 200 (and retries have
     *    been exhausted), or rewrapping a Java exception.
     */
    protected final String sendRequest(String requestMethod, String urlSuffix, String requestBody, ProgressMonitor monitor,
            boolean doAuthenticate, boolean fastFail) throws OsmTransferException {
        int retries = fastFail ? 0 : getMaxRetries();

        while (true) { // the retry loop
            try {
                url = new URL(new URL(getBaseUrl()), urlSuffix);
                final HttpClient client = HttpClient.create(url, requestMethod).keepAlive(false);
                activeConnection = client;
                if (fastFail) {
                    client.setConnectTimeout(1000);
                    client.setReadTimeout(1000);
                } else {
                    // use default connect timeout from org.openstreetmap.josm.tools.HttpClient.connectTimeout
                    client.setReadTimeout(0);
                }
                if (doAuthenticate) {
                    addAuth(client);
                }

                if ("PUT".equals(requestMethod) || "POST".equals(requestMethod) || "DELETE".equals(requestMethod)) {
                    client.setHeader("Content-Type", "text/xml");
                    // It seems that certain bits of the Ruby API are very unhappy upon
                    // receipt of a PUT/POST message without a Content-length header,
                    // even if the request has no payload.
                    // Since Java will not generate a Content-length header unless
                    // we use the output stream, we create an output stream for PUT/POST
                    // even if there is no payload.
                    client.setRequestBody((requestBody != null ? requestBody : "").getBytes(StandardCharsets.UTF_8));
                }

                final HttpClient.Response response = client.connect();
                Logging.info(response.getResponseMessage());
                int retCode = response.getResponseCode();

                if (retCode >= 500 && retries-- > 0) {
                    sleepAndListen(retries, monitor);
                    Logging.info(MessageFormat.format("Starting retry {0} of {1}.", getMaxRetries() - retries, getMaxRetries()));
                    continue;
                }

                final String responseBody = response.fetchContent();

                String errorHeader = null;
                // Look for a detailed error message from the server
                if (response.getHeaderField("Error") != null) {
                    errorHeader = response.getHeaderField("Error");
                    Logging.error("Error header: " + errorHeader);
                } else if (retCode != HttpURLConnection.HTTP_OK && responseBody.length() > 0) {
                    Logging.error("Error body: " + responseBody);
                }
                activeConnection.disconnect();

                errorHeader = errorHeader == null ? null : errorHeader.trim();
                String errorBody = responseBody.length() == 0 ? null : responseBody.trim();
                switch(retCode) {
                case HttpURLConnection.HTTP_OK:
                    return responseBody;
                case HttpURLConnection.HTTP_GONE:
                    throw new OsmApiPrimitiveGoneException(errorHeader, errorBody);
                case HttpURLConnection.HTTP_CONFLICT:                   
                        throw new OsmApiException(retCode, errorHeader, errorBody);
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    CredentialsManager.getInstance().purgeCredentialsCache(RequestorType.SERVER);
                    throw new OsmApiException(retCode, errorHeader, errorBody, activeConnection.getURL().toString(),
                            doAuthenticate ? retrieveBasicAuthorizationLogin(client) : null, response.getContentType());
                default:
                    throw new OsmApiException(retCode, errorHeader, errorBody);
                }
            } catch (SocketTimeoutException | ConnectException e) {
                if (retries-- > 0) {
                    continue;
                }
                throw new OsmTransferException(e);
            } catch (IOException e) {
                throw new OsmTransferException(e);
            } catch (OsmTransferException e) {
                throw e;
            }
        }
    }

    /**
     * Replies the API capabilities.
     *
     * @return the API capabilities, or null, if the API is not initialized yet
     */
    public synchronized Capabilities getCapabilities() {
        return capabilities;
    }
    
}
