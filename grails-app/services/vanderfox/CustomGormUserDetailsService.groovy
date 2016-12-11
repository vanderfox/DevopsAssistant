package vanderfox

import grails.transaction.Transactional
import grails.plugin.springsecurity.oauthprovider.GormClientDetailsService
import org.springframework.security.oauth2.provider.*

class CustomGormClientDetailsService extends GormClientDetailsService {
    @Transactional(readOnly = true)
    ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
        super.loadClientByClientId(clientId)
    }
}