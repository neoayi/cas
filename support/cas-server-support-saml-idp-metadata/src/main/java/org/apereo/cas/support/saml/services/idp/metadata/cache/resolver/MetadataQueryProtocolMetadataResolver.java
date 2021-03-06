package org.apereo.cas.support.saml.services.idp.metadata.cache.resolver;

import org.apereo.cas.configuration.model.support.saml.idp.SamlIdPProperties;
import org.apereo.cas.support.saml.InMemoryResourceMetadataResolver;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.util.EncodingUtils;
import org.apereo.cas.util.HttpRequestUtils;
import org.apereo.cas.util.HttpUtils;

import com.google.common.io.ByteStreams;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpResponse;
import org.opensaml.saml.metadata.resolver.impl.AbstractMetadataResolver;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.LinkedHashMap;

/**
 * This is {@link MetadataQueryProtocolMetadataResolver}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Slf4j
public class MetadataQueryProtocolMetadataResolver extends UrlResourceMetadataResolver {

    public MetadataQueryProtocolMetadataResolver(final SamlIdPProperties samlIdPProperties,
                                                 final OpenSamlConfigBean configBean) {
        super(samlIdPProperties, configBean);
    }

    @Override
    protected String getMetadataLocationForService(final SamlRegisteredService service) {
        LOGGER.info("Getting metadata dynamically for [{}]", service.getName());
        return service.getMetadataLocation().replace("{0}", EncodingUtils.urlEncode(service.getServiceId()));
    }

    @Override
    protected HttpResponse fetchMetadata(final String metadataLocation) {
        val metadata = samlIdPProperties.getMetadata();
        val headers = new LinkedHashMap<String, Object>();
        headers.put("Content-Type", metadata.getSupportedContentTypes());
        headers.put("Accept", "*/*");
        return HttpUtils.getViaBasicAuth(metadataLocation, metadata.getBasicAuthnUsername(),
            samlIdPProperties.getMetadata().getBasicAuthnPassword(), headers);
    }

    /**
     * Is dynamic metadata query configured ?
     *
     * @param service the service
     * @return true/false
     */
    protected boolean isDynamicMetadataQueryConfigured(final SamlRegisteredService service) {
        return service.getMetadataLocation().trim().endsWith("/entities/{0}");
    }

    @Override
    public boolean supports(final SamlRegisteredService service) {
        return isDynamicMetadataQueryConfigured(service);
    }

    @Override
    protected boolean shouldHttpResponseStatusBeProcessed(final HttpStatus status) {
        return super.shouldHttpResponseStatusBeProcessed(status) || status == HttpStatus.NOT_MODIFIED;
    }

    @Override
    protected AbstractMetadataResolver getMetadataResolverFromResponse(final HttpResponse response, final File backupFile) throws Exception {
        if (response.getStatusLine().getStatusCode() == HttpStatus.NOT_MODIFIED.value()) {
            return new InMemoryResourceMetadataResolver(backupFile, this.configBean);
        }

        val ins = response.getEntity().getContent();
        val source = ByteStreams.toByteArray(ins);
        val bais = new ByteArrayInputStream(source);
        return new InMemoryResourceMetadataResolver(bais, this.configBean);
    }

    @Override
    public boolean isAvailable(final SamlRegisteredService service) {
        if (supports(service)) {
            val status = HttpRequestUtils.pingUrl(service.getMetadataLocation());
            return !status.isError();
        }
        return false;
    }
}
