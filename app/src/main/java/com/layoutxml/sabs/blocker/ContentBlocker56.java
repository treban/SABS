package com.layoutxml.sabs.blocker;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Patterns;

import com.layoutxml.sabs.App;
import com.layoutxml.sabs.MainActivity;
import com.layoutxml.sabs.db.AppDatabase;
import com.layoutxml.sabs.db.entity.AppInfo;
import com.layoutxml.sabs.db.entity.BlockUrl;
import com.layoutxml.sabs.db.entity.BlockUrlProvider;
import com.layoutxml.sabs.db.entity.UserBlockUrl;
import com.layoutxml.sabs.db.entity.WhiteUrl;
import com.layoutxml.sabs.utils.BlockUrlPatternsMatch;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;
import com.sec.enterprise.firewall.FirewallRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;

import javax.inject.Inject;

public class ContentBlocker56 implements ContentBlocker {
    private static ContentBlocker56 mInstance = null;
    private final String TAG = ContentBlocker56.class.getCanonicalName();

    @Nullable
    @Inject
    Firewall mFirewall;
    @Inject
    AppDatabase appDatabase;
    private int urlBlockLimit = 2700;

    private ContentBlocker56() {
        App.get().getAppComponent().inject(this);
    }

    public static ContentBlocker56 getInstance() {
        if (mInstance == null) {
            mInstance = getSync();
        }
        return mInstance;
    }

    private static synchronized ContentBlocker56 getSync() {
        if (mInstance == null) {
            mInstance = new ContentBlocker56();
        }
        return mInstance;
    }

    @Override
    public boolean enableBlocker() {
        if (isEnabled()) {
            disableBlocker();
        }
        List<WhiteUrl> whiteUrls = appDatabase.whiteUrlDao().getAll2();

        List<String> whiteUrlsString = new ArrayList<>();
        for (WhiteUrl whiteUrl : whiteUrls) {
            whiteUrlsString.add(whiteUrl.url);
        }

        List<String> denyList = new ArrayList<>();
        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);
        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);

        for (BlockUrl blockUrl : blockUrls) {
            if (whiteUrlsString.contains(blockUrl.url)) {
                continue;
            }
            if (denyList.size() > urlBlockLimit) {
                break;
            }
            if (blockUrl.url.contains("*")) {
                boolean validWildcard = BlockUrlPatternsMatch.wildcardValid(blockUrl.url);
                if (!validWildcard) {
                    continue;
                }
                denyList.add(blockUrl.url);
            } else {
                blockUrl.url = blockUrl.url.replaceAll("^(www)([0-9]{0,3})?(\\.)", "");
                boolean validDomain = BlockUrlPatternsMatch.domainValid(blockUrl.url);
                if (!validDomain) {
                    continue;
                }
                denyList.add("*" + blockUrl.url);
            }
            }
        }

        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();

        if (userBlockUrls != null && userBlockUrls.size() > 0) {
            for (UserBlockUrl userBlockUrl : userBlockUrls) {
                if (Patterns.WEB_URL.matcher(userBlockUrl.url).matches()) {
                    final String urlReady = "*" + userBlockUrl.url + "*";
                    denyList.add(urlReady);
                }
            }
        }
        if (denyList.size() > urlBlockLimit) {
            denyList = denyList.subList(0, urlBlockLimit);
        }
        List<DomainFilterRule> rules = new ArrayList<>();
        AppIdentity appIdentity = new AppIdentity("*", null);
        rules.add(new DomainFilterRule(appIdentity, denyList, new ArrayList<>()));
        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        List<AppInfo> appInfos = appDatabase.applicationInfoDao().getWhitelistedApps();
        for (AppInfo app : appInfos) {
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }
        try {
            int numRules = 2;
            FirewallRule[] portRules = new FirewallRule[2];
            portRules[0] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
            portRules[0].setIpAddress("*");
            portRules[0].setPortNumber("53");
            portRules[1] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV6);
            portRules[1].setIpAddress("*");
            portRules[1].setPortNumber("53");
            FirewallResponse[] response = mFirewall.addRules(portRules);
        }
        catch (SecurityException ex)
        {
            return false;
        }

        try {
            FirewallResponse[] response = mFirewall.addDomainFilterRules(rules);
            if (!mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(true);
            }
            if (!mFirewall.isDomainFilterReportEnabled()) {
                mFirewall.enableDomainFilterReport(true);
            }
            if (FirewallResponse.Result.SUCCESS == response[0].getResult()) {
                return true;
            } else {
                return false;
            }
        } catch (SecurityException ex) {
            return false;
        }
    }

    @Override
    public boolean disableBlocker() {
        FirewallResponse[] response;
        try {
            response = mFirewall.clearRules(Firewall.FIREWALL_ALL_RULES);
            response = mFirewall.removeDomainFilterRules(DomainFilterRule.CLEAR_ALL);
            if (mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(false);
            }
            if (mFirewall.isDomainFilterReportEnabled()) {
                mFirewall.enableDomainFilterReport(false);
            }
        } catch (SecurityException ex) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return mFirewall.isFirewallEnabled();
    }

    public void setUrlBlockLimit(int urlBlockLimit) {
        this.urlBlockLimit = urlBlockLimit;
    }

}
