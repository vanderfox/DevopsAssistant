import vanderfox.CustomGormAuthorizationCodeService
import vanderfox.CustomGormClientDetailsService

// Place your Spring DSL code here
beans = {
	gormClientDetailsService(CustomGormClientDetailsService) {
		grailsApplication = ref('grailsApplication')
		clientAdditionalInformationSerializer = ref('clientAdditionalInformationSerializer')
	}

	gormAuthorizationCodeService(CustomGormAuthorizationCodeService) {
		grailsApplication = ref('grailsApplication')
		oauth2AuthenticationSerializer = ref('oauth2AuthenticationSerializer')
	}
}
