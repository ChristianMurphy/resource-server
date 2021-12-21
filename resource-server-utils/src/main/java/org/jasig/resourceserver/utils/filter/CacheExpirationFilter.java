/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.resourceserver.utils.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jasig.resourceserver.aggr.om.Included;
import org.jasig.resourceserver.utils.aggr.ResourcesElementsProvider;
import org.jasig.resourceserver.utils.aggr.ResourcesElementsProviderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.GenericFilterBean;

/**
 * CacheExpirationFilter sets a far-future expiration timeout on the HTTP
 * response of the filtered resource.  This filter is intended for resources
 * which will not be changed, such as versioned javascript and css files.  Any
 * resources protected by this filter should be renamed upon update.
 *
 * init-params:<br/>
 *  cacheMaxAge - Sets the max age in seconds to be used in the cache headers for cached resources,
 *      defaults to 1 year (31536000).<br/>
 *
 *  regenerateHeadersInterval - The interval in milliseconds to regenerate the cache headers,
 *      defaults to 1 second (1000).
 *
 * @author Jen Bourey
 */
public class CacheExpirationFilter extends GenericFilterBean {
    private static final long YEAR_OF_MILLISECONDS = TimeUnit.DAYS.toMillis(365);

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private String cachedControlString;
    private ResourcesElementsProvider resourcesElementsProvider;

    //Default resource cache time is 1 year
    private long cacheMaxAge = YEAR_OF_MILLISECONDS;

    public int getCacheMaxAge() {
        return (int) (this.cacheMaxAge / 1000l);
    }

    /**
     * @param cacheMaxAge Sets the max age in seconds to be used in the cache headers for cached resources, defaults to 1 year (31536000)
     */
    public void setCacheMaxAge(int cacheMaxAge) {
        if (cacheMaxAge < 1) {
            throw new IllegalArgumentException("Specified initParamter 'cacheMaxAge' must be greater than 0, ("
                    + cacheMaxAge + ")");
        }

        this.cacheMaxAge = cacheMaxAge * 1000;

        this.updateHeaders();
    }

    /**
     * ResourcesElementsProvider, if not set {@link ResourcesElementsProviderUtils#getOrCreateResourcesElementsProvider(javax.servlet.ServletContext)} is used.
     */
    public void setResourcesElementsProvider(ResourcesElementsProvider resourcesElementsProvider) {
        this.resourcesElementsProvider = resourcesElementsProvider;
    }

    /* (non-Javadoc)
     * @see org.springframework.web.filter.GenericFilterBean#initFilterBean()
     */
    @Override
    protected void initFilterBean() throws ServletException {
        if (this.resourcesElementsProvider == null) {
            final ServletContext servletContext = this.getServletContext();
            this.resourcesElementsProvider = ResourcesElementsProviderUtils.getOrCreateResourcesElementsProvider(servletContext);
        }

        this.updateHeaders();
    }

    /**
     *
     */
    private void updateHeaders() {
        //Generate cache control value
        this.cachedControlString = "public, max-age=" + this.cacheMaxAge / 1000;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {

        // add the cache expiration time to the response
        if (response instanceof HttpServletResponse && request instanceof HttpServletRequest) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest)request;
            final Included includedType = this.resourcesElementsProvider.getIncludedType(httpServletRequest);
            if (includedType == Included.AGGREGATED) {
                final HttpServletResponse httpResponse = (HttpServletResponse) response;

                httpResponse.setDateHeader("Expires", this.cacheMaxAge + System.currentTimeMillis());
                httpResponse.setHeader("Cache-Control", this.cachedControlString);
            }
        }

        // continue
        chain.doFilter(request, response);
    }
}
