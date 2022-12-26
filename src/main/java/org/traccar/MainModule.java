/*
 * Copyright 2018 - 2022 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.NullLogChute;
import org.eclipse.jetty.util.URIUtil;
import org.traccar.broadcast.BroadcastService;
import org.traccar.broadcast.MulticastBroadcastService;
import org.traccar.broadcast.NullBroadcastService;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.LdapProvider;
import org.traccar.database.StatisticsManager;
import org.traccar.forward.EventForwarder;
import org.traccar.forward.EventForwarderJson;
import org.traccar.forward.EventForwarderKafka;
import org.traccar.forward.EventForwarderMqtt;
import org.traccar.forward.PositionForwarder;
import org.traccar.forward.PositionForwarderJson;
import org.traccar.forward.PositionForwarderKafka;
import org.traccar.forward.PositionForwarderUrl;
import org.traccar.geocoder.AddressFormat;
import org.traccar.geocoder.BanGeocoder;
import org.traccar.geocoder.BingMapsGeocoder;
import org.traccar.geocoder.FactualGeocoder;
import org.traccar.geocoder.GeoapifyGeocoder;
import org.traccar.geocoder.GeocodeFarmGeocoder;
import org.traccar.geocoder.GeocodeXyzGeocoder;
import org.traccar.geocoder.Geocoder;
import org.traccar.geocoder.GisgraphyGeocoder;
import org.traccar.geocoder.GoogleGeocoder;
import org.traccar.geocoder.HereGeocoder;
import org.traccar.geocoder.LocationIqGeocoder;
import org.traccar.geocoder.MapQuestGeocoder;
import org.traccar.geocoder.MapTilerGeocoder;
import org.traccar.geocoder.MapboxGeocoder;
import org.traccar.geocoder.MapmyIndiaGeocoder;
import org.traccar.geocoder.NominatimGeocoder;
import org.traccar.geocoder.OpenCageGeocoder;
import org.traccar.geocoder.PositionStackGeocoder;
import org.traccar.geocoder.TestGeocoder;
import org.traccar.geocoder.TomTomGeocoder;
import org.traccar.geolocation.GeolocationProvider;
import org.traccar.geolocation.GoogleGeolocationProvider;
import org.traccar.geolocation.MozillaGeolocationProvider;
import org.traccar.geolocation.OpenCellIdGeolocationProvider;
import org.traccar.geolocation.UnwiredGeolocationProvider;
import org.traccar.handler.GeocoderHandler;
import org.traccar.handler.GeolocationHandler;
import org.traccar.handler.SpeedLimitHandler;
import org.traccar.helper.ObjectMapperContextResolver;
import org.traccar.helper.SanitizerModule;
import org.traccar.mail.LogMailManager;
import org.traccar.mail.MailManager;
import org.traccar.mail.SmtpMailManager;
import org.traccar.session.cache.CacheManager;
import org.traccar.sms.HttpSmsClient;
import org.traccar.sms.SmsManager;
import org.traccar.sms.SnsSmsClient;
import org.traccar.speedlimit.OverpassSpeedLimitProvider;
import org.traccar.speedlimit.SpeedLimitProvider;
import org.traccar.storage.DatabaseStorage;
import org.traccar.storage.Storage;
import org.traccar.web.WebServer;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class MainModule extends AbstractModule {

    private final String configFile;

    public MainModule(String configFile) {
        this.configFile = configFile;
    }

    @Override
    protected void configure() {
        bindConstant().annotatedWith(Names.named("configFile")).to(configFile);
        bind(Config.class).asEagerSingleton();
        bind(Storage.class).to(DatabaseStorage.class).in(Scopes.SINGLETON);
        bind(Timer.class).to(HashedWheelTimer.class).in(Scopes.SINGLETON);
    }

    @Singleton
    @Provides
    public static ObjectMapper provideObjectMapper(Config config) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (config.getBoolean(Keys.WEB_SANITIZE)) {
            objectMapper.registerModule(new SanitizerModule());
        }
        objectMapper.registerModule(new JSR353Module());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Singleton
    @Provides
    public static Client provideClient(ObjectMapperContextResolver objectMapperContextResolver) {
        return ClientBuilder.newClient().register(objectMapperContextResolver);
    }

    @Singleton
    @Provides
    public static SmsManager provideSmsManager(Config config, Client client) {
        if (config.hasKey(Keys.SMS_HTTP_URL)) {
            return new HttpSmsClient(config, client);
        } else if (config.hasKey(Keys.SMS_AWS_REGION)) {
            return new SnsSmsClient(config);
        }
        return null;
    }

    @Singleton
    @Provides
    public static MailManager provideMailManager(Config config, StatisticsManager statisticsManager) {
        if (config.getBoolean(Keys.MAIL_DEBUG)) {
            return new LogMailManager();
        } else {
            return new SmtpMailManager(config, statisticsManager);
        }
    }

    @Singleton
    @Provides
    public static LdapProvider provideLdapProvider(Config config) {
        if (config.hasKey(Keys.LDAP_URL)) {
            return new LdapProvider(config);
        }
        return null;
    }

    @Provides
    public static WebServer provideWebServer(Injector injector, Config config) {
        if (config.hasKey(Keys.WEB_PORT)) {
            return new WebServer(injector, config);
        }
        return null;
    }

    @Singleton
    @Provides
    public static Geocoder provideGeocoder(Config config, Client client, StatisticsManager statisticsManager) {
        if (config.getBoolean(Keys.GEOCODER_ENABLE)) {
            String type = config.getString(Keys.GEOCODER_TYPE, "google");
            String url = config.getString(Keys.GEOCODER_URL);
            String id = config.getString(Keys.GEOCODER_ID);
            String key = config.getString(Keys.GEOCODER_KEY);
            String language = config.getString(Keys.GEOCODER_LANGUAGE);
            String formatString = config.getString(Keys.GEOCODER_FORMAT);
            AddressFormat addressFormat = formatString != null ? new AddressFormat(formatString) : new AddressFormat();

            int cacheSize = config.getInteger(Keys.GEOCODER_CACHE_SIZE);
            Geocoder geocoder;
            switch (type) {
                case "test":
                    geocoder = new TestGeocoder();
                    break;
                case "nominatim":
                    geocoder = new NominatimGeocoder(client, url, key, language, cacheSize, addressFormat);
                    break;
                case "locationiq":
                    geocoder = new LocationIqGeocoder(client, url, key, language, cacheSize, addressFormat);
                    break;
                case "gisgraphy":
                    geocoder = new GisgraphyGeocoder(client, url, cacheSize, addressFormat);
                    break;
                case "mapquest":
                    geocoder = new MapQuestGeocoder(client, url, key, cacheSize, addressFormat);
                    break;
                case "opencage":
                    geocoder = new OpenCageGeocoder(client, url, key, language, cacheSize, addressFormat);
                    break;
                case "bingmaps":
                    geocoder = new BingMapsGeocoder(client, url, key, cacheSize, addressFormat);
                    break;
                case "factual":
                    geocoder = new FactualGeocoder(client, url, key, cacheSize, addressFormat);
                    break;
                case "geocodefarm":
                    geocoder = new GeocodeFarmGeocoder(client, key, language, cacheSize, addressFormat);
                    break;
                case "geocodexyz":
                    geocoder = new GeocodeXyzGeocoder(client, key, cacheSize, addressFormat);
                    break;
                case "ban":
                    geocoder = new BanGeocoder(client, cacheSize, addressFormat);
                    break;
                case "here":
                    geocoder = new HereGeocoder(client, url, id, key, language, cacheSize, addressFormat);
                    break;
                case "mapmyindia":
                    geocoder = new MapmyIndiaGeocoder(client, url, key, cacheSize, addressFormat);
                    break;
                case "tomtom":
                    geocoder = new TomTomGeocoder(client, url, key, cacheSize, addressFormat);
                    break;
                case "positionstack":
                    geocoder = new PositionStackGeocoder(client, key, cacheSize, addressFormat);
                    break;
                case "mapbox":
                    geocoder = new MapboxGeocoder(client, key, cacheSize, addressFormat);
                    break;
                case "maptiler":
                    geocoder = new MapTilerGeocoder(client, key, cacheSize, addressFormat);
                    break;
                case "geoapify":
                    geocoder = new GeoapifyGeocoder(client, key, language, cacheSize, addressFormat);
                    break;
                default:
                    geocoder = new GoogleGeocoder(client, key, language, cacheSize, addressFormat);
                    break;
            }
            geocoder.setStatisticsManager(statisticsManager);
            return geocoder;
        }
        return null;
    }

    @Singleton
    @Provides
    public static GeolocationProvider provideGeolocationProvider(Config config, Client client) {
        if (config.getBoolean(Keys.GEOLOCATION_ENABLE)) {
            String type = config.getString(Keys.GEOLOCATION_TYPE, "mozilla");
            String url = config.getString(Keys.GEOLOCATION_URL);
            String key = config.getString(Keys.GEOLOCATION_KEY);
            switch (type) {
                case "google":
                    return new GoogleGeolocationProvider(client, key);
                case "opencellid":
                    return new OpenCellIdGeolocationProvider(client, url, key);
                case "unwired":
                    return new UnwiredGeolocationProvider(client, url, key);
                default:
                    return new MozillaGeolocationProvider(client, key);
            }
        }
        return null;
    }

    @Singleton
    @Provides
    public static SpeedLimitProvider provideSpeedLimitProvider(Config config, Client client) {
        if (config.getBoolean(Keys.SPEED_LIMIT_ENABLE)) {
            String type = config.getString(Keys.SPEED_LIMIT_TYPE, "overpass");
            String url = config.getString(Keys.SPEED_LIMIT_URL);
            switch (type) {
                case "overpass":
                default:
                    return new OverpassSpeedLimitProvider(client, url);
            }
        }
        return null;
    }

    @Singleton
    @Provides
    public static GeolocationHandler provideGeolocationHandler(
            Config config, @Nullable GeolocationProvider geolocationProvider, CacheManager cacheManager,
            StatisticsManager statisticsManager) {
        if (geolocationProvider != null) {
            return new GeolocationHandler(config, geolocationProvider, cacheManager, statisticsManager);
        }
        return null;
    }

    @Singleton
    @Provides
    public static GeocoderHandler provideGeocoderHandler(
            Config config, @Nullable Geocoder geocoder, CacheManager cacheManager) {
        if (geocoder != null) {
            return new GeocoderHandler(config, geocoder, cacheManager);
        }
        return null;
    }

    @Singleton
    @Provides
    public static SpeedLimitHandler provideSpeedLimitHandler(@Nullable SpeedLimitProvider speedLimitProvider) {
        if (speedLimitProvider != null) {
            return new SpeedLimitHandler(speedLimitProvider);
        }
        return null;
    }

    @Singleton
    @Provides
    public static BroadcastService provideBroadcastService(
            Config config, ObjectMapper objectMapper) throws IOException {
        if (config.hasKey(Keys.BROADCAST_ADDRESS)) {
            return new MulticastBroadcastService(config, objectMapper);
        }
        return new NullBroadcastService();
    }

    @Singleton
    @Provides
    public static EventForwarder provideEventForwarder(Config config, Client client, ObjectMapper objectMapper) {
        if (config.hasKey(Keys.EVENT_FORWARD_URL)) {
            String forwardType = config.getString(Keys.EVENT_FORWARD_TYPE);
            switch (forwardType) {
                case "kafka":
                    return new EventForwarderKafka(config, objectMapper);
                case "mqtt":
                    return new EventForwarderMqtt(config, objectMapper);
                default:
                    return new EventForwarderJson(config, client);
            }
        }
        return null;
    }

    @Singleton
    @Provides
    public static PositionForwarder providePositionForwarder(Config config, Client client, ObjectMapper objectMapper) {
        if (config.hasKey(Keys.FORWARD_URL)) {
            switch (config.getString(Keys.FORWARD_TYPE)) {
                case "json":
                    return new PositionForwarderJson(config, client, objectMapper);
                case "kafka":
                    return new PositionForwarderKafka(config, objectMapper);
                default:
                    return new PositionForwarderUrl(config, client, objectMapper);
            }
        }
        return null;
    }

    @Singleton
    @Provides
    public static VelocityEngine provideVelocityEngine(Config config) {
        Properties properties = new Properties();
        properties.setProperty("file.resource.loader.path", config.getString(Keys.TEMPLATES_ROOT) + "/");
        properties.setProperty("runtime.log.logsystem.class", NullLogChute.class.getName());

        if (config.hasKey(Keys.WEB_URL)) {
            properties.setProperty("web.url", config.getString(Keys.WEB_URL).replaceAll("/$", ""));
        } else {
            String address;
            try {
                address = config.getString(Keys.WEB_ADDRESS, InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                address = "localhost";
            }
            String url = URIUtil.newURI("http", address, config.getInteger(Keys.WEB_PORT), "", "");
            properties.setProperty("web.url", url);
        }

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);
        return velocityEngine;
    }

}
