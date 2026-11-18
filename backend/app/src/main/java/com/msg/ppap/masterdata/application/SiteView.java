package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.Site;

/**
 * Site with derived data.
 *
 * @param site the site
 * @param ownerName name of the owning tenant or partner ({@code null} if unknown)
 */
public record SiteView(Site site, String ownerName) {}
