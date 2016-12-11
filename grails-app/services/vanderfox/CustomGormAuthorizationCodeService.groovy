package vanderfox

import grails.transaction.Transactional
import grails.plugin.springsecurity.oauthprovider.GormAuthorizationCodeService
import org.springframework.security.oauth2.provider.OAuth2Authentication

class CustomGormAuthorizationCodeService extends GormAuthorizationCodeService {
    @Transactional(readOnly = true) //this will make sure hibernate session is available
    protected void store(String code, OAuth2Authentication authentication) {
        super.store(code, authentication)
    }

    @Transactional(readOnly = true)
    protected OAuth2Authentication remove(String code) {
        super.remove(code)
    }
}