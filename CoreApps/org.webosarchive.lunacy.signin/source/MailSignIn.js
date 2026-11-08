/*
 * Signing a Google or Microsoft mail account in, for Accounts: the account types' own page
 * (validator.customUI), shown instead of a username and password form, since these providers
 * no longer take a password from a mail client (Docs/oauth.md).
 *
 * Lunacy's OAuth service (palm://org.webosarchive.lunacy.oauth) does the signing in, in one of
 * two ways, by what this device can do:
 * - here, on the provider's own page: in this page's web view, its redirect caught with
 *   webOS's `redirects` as webOS apps caught Facebook's; or in Android's browser, for a
 *   provider that refuses web views (Google);
 * - or on another device, through the sign-in helper (the broker): this page shows an
 *   address and a short code, and the service waits for the helper's tokens.
 * Either way the service answers with a handle for the account's tokens, which goes to Palm's
 * IMAP service as the password: it checks the account against the server, as Email's wizard
 * checks one, and the page hands Accounts the account to make.
 *
 * Microsoft has one entry for every kind of account: Exchange ActiveSync for work and school
 * accounts, and, for a personal account (Outlook.com), which Microsoft refuses Exchange's
 * permission, IMAP. The service signs a refused account in again with the IMAP provider (its
 * `fallback`) and says so with that provider's account type, which this page hands Accounts in
 * place of the one tapped (Docs/outlook-eas.md).
 */
enyo.kind({
	name: "MailSignIn",
	kind: "VFlexBox",
	className: "enyo-bg",
	width: "100%",
	height: "100%",
	published: {
		params: null
	},
	components: [
		{kind: "Toolbar", pack: "center", className: "enyo-toolbar-light accounts-header", components: [
			{name: "header", kind: "Control"}
		]},
		{className: "accounts-header-shadow"},
		{name: "pane", kind: "Pane", flex: 1, transitionKind: "enyo.transitions.Simple", components: [
			{name: "intro", kind: "Scroller", components: [
				{kind: "Control", className: "box-center", components: [
					{name: "title", className: "accounts-body-title"},
					{name: "text", className: "accounts-body-text", style: "margin-top: 8px;"},
					{name: "problem", className: "accounts-body-text", style: "margin-top: 16px; color: #b00;", showing: false},
					{name: "hereButton", kind: "ActivityButton", className: "enyo-button-affirmative", style: "margin-top: 16px;", caption: $L("Sign In"), onclick: "signInHere"},
					{name: "brokerButton", kind: "ActivityButton", style: "margin-top: 8px;", caption: $L("Sign In on Another Device"), onclick: "signInElsewhere"},
					{name: "remove", kind: "Accounts.RemoveAccount", style: "margin-top: 16px;", showing: false, onAccountsRemove_Done: "cancel"}
				]}
			]},
			{name: "page", kind: "VFlexBox", components: [
				{name: "web", kind: "WebView", flex: 1, onUrlRedirected: "pageRedirected", onLoadComplete: "pageLoaded"}
			]},
			{name: "waiting", kind: "Scroller", components: [
				{kind: "Control", className: "box-center", components: [
					{name: "waitTitle", className: "accounts-body-title"},
					{name: "waitText", className: "accounts-body-text", style: "margin-top: 8px;"},
					{name: "code", style: "margin: 24px 0; font-size: 40px; letter-spacing: 6px; text-align: center; font-weight: bold;", showing: false},
					{name: "address", className: "accounts-body-text", style: "text-align: center;", showing: false},
					{kind: "HFlexBox", pack: "center", style: "margin-top: 24px;", components: [{name: "spinner", kind: "SpinnerLarge"}]}
				]}
			]}
		]},
		{className: "accounts-footer-shadow"},
		{kind: "Toolbar", className: "enyo-toolbar-light", components: [
			{name: "cancelButton", kind: "Button", className: "accounts-toolbar-btn", caption: $L("Cancel"), onclick: "cancel"}
		]},
		{kind: "CrossAppResult"},
		{name: "providers", kind: "PalmService", service: "palm://org.webosarchive.lunacy.oauth/", method: "listProviders", onResponse: "gotProviders"},
		{name: "signIn", kind: "PalmService", service: "palm://org.webosarchive.lunacy.oauth/", method: "signIn", subscribe: true, onResponse: "signInStep"},
		{name: "redirected", kind: "PalmService", service: "palm://org.webosarchive.lunacy.oauth/", method: "redirected"},
		{name: "forget", kind: "PalmService", service: "palm://org.webosarchive.lunacy.oauth/", method: "forget"},
		// The account type a fallback sign-in made the account (hidden from Add Account, so not in params).
		{name: "templates", kind: "PalmService", service: "palm://com.palm.service.accounts/", method: "listAccountTemplates", onResponse: "gotTemplates"},
		{name: "validate", kind: "PalmService", service: "palm://com.palm.imap/", method: "validateAccount", onResponse: "validated"},
		{name: "modifyAccount", kind: "PalmService", service: "palm://com.palm.service.accounts/", method: "modifyAccount", onResponse: "modified"},
		// An Exchange server's security policy, as Email's wizard handles one (CRUDAccounts.handlePasswordPolicy):
		// the policy the service downloaded, and webOS's own prompt to set the PIN or password it asks for.
		{name: "getPolicy", kind: "PalmService", service: "palm://com.palm.systemmanager/", method: "getSecurityPolicy", onResponse: "gotPolicy"},
		{name: "guard", kind: "SecurityPolicyGuard", onSuccess: "policyMet", onFailure: "policyRefused", onCancel: "policyRefused"}
	],

	create: function () {
		this.inherited(arguments);
		var p = this.params || {};
		this.templateId = (p.template && p.template.templateId) || (p.account && p.account.templateId) || "";
		this.$.header.setContent((p.template && p.template.loc_name) || (p.account && (p.account.loc_name || p.account.alias)) || $L("Sign In"));
		this.$.hereButton.hide();
		this.$.brokerButton.hide();
		if (p.mode === "modify") {
			// The account's own page: signing in again (Email sends a "bad credentials" account here)
			// and Accounts' Remove Account.
			this.modifying = p.account;
			this.$.cancelButton.setCaption($L("Done"));
			this.$.remove.show();
			this.$.remove.init(p.account);
		}
		this.$.providers.call({});
	},

	gotProviders: function (inSender, r) {
		var list = (r && r.providers) || [], i, id = this.templateId.substring(this.templateId.lastIndexOf(".") + 1);
		for (i = 0; i < list.length; i++) {
			if (list[i].id === id) {
				this.provider = list[i];
			}
		}
		var name = this.provider ? this.provider.title : this.$.header.getContent();
		if (!this.provider || (!this.provider.onDevice && !this.provider.broker)) {
			this.$.title.setContent($L("Not available yet"));
			this.$.text.setContent(new enyo.g11n.Template($L("Signing in to #{name} isn't set up in this version of Lunacy.")).evaluate({name: name}));
			return;
		}
		if (this.modifying) {
			this.$.title.setContent(this.modifying.username || name);
			this.$.text.setContent(new enyo.g11n.Template($L("If Email can't reach this account any more, sign in to #{name} again. Your password goes to #{name} only, never to Lunacy.")).evaluate({name: name}));
			this.$.hereButton.setCaption($L("Sign In Again"));
		} else {
			this.$.title.setContent(new enyo.g11n.Template($L("Sign in to #{name}")).evaluate({name: name}));
			// An Exchange account (Microsoft 365) brings contacts and calendar as well as mail.
			var cps = (this.params && this.params.template && this.params.template.capabilityProviders) || [], more = false;
			for (i = 0; i < cps.length; i++) {
				if (cps[i].capability === "CONTACTS" || cps[i].capability === "CALENDAR") {
					more = true;
				}
			}
			this.$.text.setContent(new enyo.g11n.Template(more
				? $L("#{name} asks you to sign in on its own page. Your password goes to #{name} only, never to Lunacy; Lunacy is given permission to keep your mail, contacts and calendar.")
				: $L("#{name} asks you to sign in on its own page. Your password goes to #{name} only, never to Lunacy; Email is given permission to read and send your mail.")).evaluate({name: name}));
		}
		this.$.hereButton.setShowing(Boolean(this.provider.onDevice));
		this.$.brokerButton.setShowing(Boolean(this.provider.broker));
		if (!this.provider.onDevice) {
			this.$.brokerButton.setCaption($L("Sign In"));
			this.$.brokerButton.addClass("enyo-button-affirmative");
		}
	},

	signInHere: function () { this.start("device"); },
	signInElsewhere: function () { this.start("broker"); },

	start: function (way) {
		this.$.problem.hide();
		this.way = way;
		this.forgetHandle();
		this.$.signIn.cancel();
		// A new account may become another type than the one tapped (Microsoft's personal accounts);
		// signing an account in again keeps its type.
		this.templateId = this.firstTemplateId || this.templateId;
		this.firstTemplateId = this.templateId;
		this.$.signIn.call({provider: this.provider.id, way: way, fallback: !this.modifying});
		(way === "broker" ? this.$.brokerButton : this.$.hereButton).setActive(true);
	},

	signInStep: function (inSender, r) {
		if (!r || !r.returnValue) {
			this.failed(this.refusal(r) || (r && r.errorText) || $L("Signing in didn't work."));
			return;
		}
		if (r.step === "page") {
			this.$.web.setRedirects([{regex: "^" + r.redirect.replace(/[.*+?^${}()|[\]\\\/]/g, "\\$&"), cookie: "signin", enable: true}]);
			this.$.pane.selectView(this.$.page);
			this.$.web.setUrl(r.url);
		} else if (r.step === "browser") {
			this.showWaiting($L("Finish signing in in the browser"), $L("The browser has the sign-in page. When you're done there, come back here."));
		} else if (r.step === "code") {
			this.showWaiting($L("Sign in on another device"), $L("On a phone or computer, go to this address and enter the code:"));
			this.$.address.setContent(r.url);
			this.$.code.setContent(r.code);
			this.$.address.show();
			this.$.code.show();
		} else if (r.step === "done") {
			// A handle an account already has (the same account signed in before) is never forgotten here.
			this.handle = r.existing ? null : r.handle;
			this.signedIn = r.handle;
			this.email = r.email;
			this.config = r.config;
			// Signed in with the fallback provider: the account is of its type.
			this.fellBack = Boolean(r.templateId && r.templateId !== this.templateId && !this.modifying);
			if (this.fellBack) {
				this.templateId = r.templateId;
			}
			this.showWaiting($L("Checking your account"), this.fellBack
				? new enyo.g11n.Template($L("#{email} is a personal Microsoft account. Microsoft gives Email its mail, but not its contacts or calendar.")).evaluate({email: r.email})
				: r.email);
			this.$.address.hide();
			this.$.code.hide();
			if (this.modifying && this.modifying.username && r.email.toLowerCase() !== this.modifying.username.toLowerCase()) {
				this.failed(new enyo.g11n.Template($L("That was #{other}. Sign in as #{email} to keep this account.")).evaluate({other: r.email, email: this.modifying.username}));
				return;
			}
			// The provider's mail service checks the account, as Email's wizard has it checked:
			// Palm's IMAP service, or its Exchange ActiveSync one.
			var v = /^(palm:\/\/[^\/]+\/)(.+)$/.exec(r.validator || "palm://com.palm.imap/validateAccount");
			this.$.validate.setService(v[1]);
			this.$.validate.setMethod(v[2]);
			this.$.validate.call({username: r.email, password: r.handle, templateId: this.templateId, config: r.config});
		}
	},

	/*
	 * A refusal the provider explained (the OAuth service passes its error and, for Microsoft,
	 * the AADSTS number), in plain words, with Microsoft's code kept for whoever helps. Microsoft's
	 * codes: 65001, 90094, 90095 an administrator's consent needed; 65004 the person declined
	 * (Microsoft's "Need admin approval" page leads there too: access_denied); 50105 the person
	 * isn't assigned to the app; 53003 the organisation's sign-in rules (Conditional Access)
	 * blocked it. Null for anything else: the service's own text says it.
	 */
	refusal: function (r) {
		var code = r && r.aadsts, error = r && r.providerError, name = (this.provider && this.provider.title) || "";
		var t;
		if (code === 65001 || code === 90094 || code === 90095 || error === "consent_required") {
			t = $L("Your organization's administrator has to approve the app \"webOS Email\" before Lunacy can reach this account. Ask them to approve it, then sign in again.");
		} else if (code === 65004 || error === "access_denied") {
			t = new enyo.g11n.Template($L("#{name} didn't give Lunacy permission to reach this account. Sign in again and accept; if the account belongs to work or school and #{name} asks for an administrator's approval, your organization's administrator has to approve the app \"webOS Email\" first.")).evaluate({name: name});
		} else if (code === 50105) {
			t = $L("Your organization hasn't given you access to the app \"webOS Email\". Ask its administrator to allow it, then sign in again.");
		} else if (code === 53003) {
			t = $L("Your organization's sign-in rules don't allow this device or app. Ask its administrator.");
		} else {
			return null;
		}
		return code ? t + " (AADSTS" + code + ")" : t;
	},

	showWaiting: function (title, text) {
		this.$.waitTitle.setContent(title);
		this.$.waitText.setContent(text);
		this.$.spinner.show();
		this.$.pane.selectView(this.$.waiting);
	},

	pageRedirected: function (inSender, inUrl) {
		this.showWaiting($L("Signing in"), "");
		this.$.redirected.call({url: inUrl});
	},

	pageLoaded: function () {
	},

	validated: function (inSender, r) {
		// 2500: the account is fine, and its Exchange server sent a security policy to meet first
		// (EmailAccountValidation.SECURITY_POLICY_CHECK_NEEDED; the mail services say it in
		// mailErrorCode, as Email's wizard reads it).
		if (r && !r.returnValue && (r.mailErrorCode === 2500 || r.errorCode === 2500)) {
			this.$.getPolicy.call({});
			return;
		}
		// 1110 (ACCOUNT_UNAVAILABLE): the server asked to be left alone for a while (a throttled
		// Exchange; the preload's easerr.h), which is no fault of the sign-in.
		if (r && !r.returnValue && (r.mailErrorCode === 1110 || r.errorCode === "503_SERVICE_UNAVAILABLE")) {
			this.failed(new enyo.g11n.Template($L("#{email} signed in, but its mail server is busy and asked to be left alone for a while. Try again in a few minutes.")).evaluate({email: this.email}));
			this.forgetHandle();
			return;
		}
		if (!r || !r.returnValue) {
			this.failed(new enyo.g11n.Template($L("#{email} signed in, but its mail server didn't take the sign-in. #{why}")).evaluate({
				email: this.email, why: (r && r.errorText) || ""}));
			this.forgetHandle();
			return;
		}
		var config = (r.protocolSettings && r.protocolSettings.config) || this.config;
		if (this.modifying) {
			// Accounts takes nothing back from an account's page in modify mode: the page saves the
			// credentials itself, and the mail service hears of it (onCredentialsChanged) and tries again.
			this.$.modifyAccount.call({accountId: this.modifying._id, object: {credentials: {common: {password: this.signedIn}}}});
			this.handle = null;
			return;
		}
		this.validConfig = config;
		if (this.fellBack) {
			this.$.templates.call({});
			return;
		}
		this.made((this.params || {}).template);
	},

	gotTemplates: function (inSender, r) {
		var list = (r && r.results) || [], i;
		for (i = 0; i < list.length; i++) {
			if (list[i].templateId === this.templateId) {
				this.made(list[i]);
				return;
			}
		}
		this.failed($L("Signed in, but the account couldn't be saved.") + " " + this.templateId);
		this.forgetHandle();
	},

	// Accounts' own pages take the template back with the account (entry-first-launch.js).
	made: function (template) {
		var config = this.validConfig;
		this.$.crossAppResult.sendResult({
			returnValue: true,
			template: template,
			templateId: this.templateId,
			username: this.email,
			alias: this.$.header.getContent(),
			credentials: {common: {password: this.signedIn}},
			config: config
		});
		this.handle = null;
	},

	gotPolicy: function (inSender, r) {
		var policy = r && r.policy;
		var status = policy && policy.status, password = policy && policy.password;
		if (status && status.enforced === false && password && password.enabled === true) {
			policy.id = this.email;   // as Email's wizard does: the policy is this account's
			this.$.guard.setPolicy(policy);
			return;
		}
		this.policyMet();
	},

	// The policy is met: the account is made from what the sign-in gave, as Email's wizard does.
	policyMet: function () {
		this.validated(null, {returnValue: true});
	},

	policyRefused: function () {
		this.failed($L("This account's server requires a screen lock on this device. Set one when asked to add the account."));
		this.forgetHandle();
	},

	modified: function (inSender, r) {
		if (!r || !r.returnValue) {
			this.failed($L("Signed in, but the account couldn't be saved.") + " " + ((r && r.errorText) || ""));
			return;
		}
		this.$.crossAppResult.sendResult({returnValue: true});
	},

	failed: function (text) {
		this.$.signIn.cancel();
		this.$.hereButton.setActive(false);
		this.$.brokerButton.setActive(false);
		this.$.spinner.hide();
		this.$.problem.setContent(text);
		this.$.problem.show();
		this.$.pane.selectView(this.$.intro);
	},

	// A handle made by a sign-in that wasn't kept: its tokens go too. Not in modify mode, where
	// the handle is the account's own (signing in again keeps it).
	forgetHandle: function () {
		if (this.handle && !this.modifying) {
			this.$.forget.call({handle: this.handle});
			this.handle = null;
		}
	},

	cancel: function () {
		this.$.signIn.cancel();
		this.forgetHandle();
		this.$.crossAppResult.sendResult({returnValue: false});
	}
});
