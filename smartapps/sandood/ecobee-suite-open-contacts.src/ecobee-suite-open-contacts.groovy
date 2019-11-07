/**
 *  ecobee Suite Open Contacts
 *
 *  Copyright 2016 Sean Kendall Schneyer
 *	Copyright 2017-19 Barry A. Burke *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you/**
 *  ecobee Suite Open Contacts
 *      http://www.apache.org/licenses/LICENSE-2.0  
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * <snip>
 *	1.7.00 - Initial Release of Universal Ecobee Suite
 *	1.7.01 - nonCached currentValue() for HE
 *	1.7.02 - Fixed initialization error
 *	1.7.03 - Cosmetic cleanup, and nonCached currentValue() on Hubitat
 * 	1.7.04 - Fix myThermostats (should have been theThermostats)
 *	1.7.05 - More nonCached cleanup
 *	1.7.06 - Fixed multi-contact/multi-switch initialization
 *	1.7.07 - Fixed SMS text entry
 *	1.7.08 - Don't turn HVAC On if it was Off when the first contact/switch would have turned it Off
 *	1.7.09 - Fixing private method issue caused by grails, handle my/theThermostats, fix therm.displayName
 *	1.7.10 - Fixed statModeChange() event handler
 *	1.7.11 - Prevent duplicate notifications
 *	1.7.12 - On HE, changed (paused) banner to match Hubitat Simple Lighting's (pause)
 *	1.7.13 - Wasn't saving thermState when turning back on
 *	1.7.14 - Fix thermSavedState initialization
 *	1.7.15 - And fixed it some more
 *	1.7.16 - And still more
 *	1.7.17 - Optimized isST/isHE, more multi-stat/multi-contact work, Global Pause
 *	1.7.18 - Fixed therm.currentValue typo
 *	1.7.19 - Fixed optimization typo
 *	1.7.20 - Fixed accidental double-paste into GitHub
 *	1.7.21 - Yet another type...
 *	1.7.22 - Double check everything is still open before turning off
 *	1.7.23 - Cleaned up unschedule()
 *	1.7.24 - Fixed ADT (another damned typo)
 *	1.7.25 - Don't notify if contacts are closed while "off_pending" delay
 *	1.7.26 - Fixed 'off_pending' (again)
 *	1.7.27 - Changed minimum LOG level to 3
 *	1.7.28 - Fixed unintended overwrite of thermostat's mode in statModeChange()
 *	1.7.29 - Clean up appLabel in sendMessage()
 *	1.7.30 - Corrected (user reported) tmpThermSaveState typo
 *	1.7.31 - Added noDebug option
 *	1.7.32 - Tweaked noDebug, add Notifications device support for ST
 *	1.7.33 - Cleaned up Notification messages for single/multi thermostats
 *	1.7.34 - Fixed Notifications section for ST, removed SMS for HE
 *	1.7.35 - Fixed initialization error when HVAC should be off
 *	1.7.36 - Added options to customize the Notifications
 *	1.7.37 - Bypass HE cache for currentContact/currentSwitch
 */
String getVersionNum() 		{ return "1.7.37" }
String getVersionLabel() 	{ return "Ecobee Suite Contacts & Switches Helper, version ${getVersionNum()} on ${getHubPlatform()}" }

definition(
	name: 			"ecobee Suite Open Contacts",
	namespace: 		"sandood",
	author: 		"Barry A. Burke (storageanarchy at gmail dot com)",
	description: 	"INSTALL USING ECOBEE SUITE MANAGER ONLY!\n\nTurn HVAC on/off based on status of contact sensors or switches (e.g. doors, windows, or fans)",
	category: 		"Convenience",
	parent: 		"sandood:Ecobee Suite Manager",
	iconUrl: 		"https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: 		"https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: false,
    pausable: 		true
)

preferences {
	page(name: "mainPage")
	page(name: "customNotifications")
}

// Preferences Pages
def mainPage() {
	boolean ST = isST
	boolean HE = !ST
	dynamicPage(name: "mainPage", title: (HE?'<b>':'') + getVersionLabel() + (HE?'</b>':''), uninstall: true, install: true) {
    	section(title: "") {
        	String defaultLabel = "Contacts & Switches"
        	label(title: "Name for this ${defaultLabel} Helper", required: true, defaultValue: defaultLabel)
            if (!app.label) {
				app.updateLabel(defaultLabel)
				atomicState.appDisplayName = defaultLabel
			}
			if (HE) {
				if (app.label.contains('<span ')) {
					if (atomicState?.appDisplayName != null) {
						app.updateLabel(atomicState.appDisplayName)
					} else {
						String myLabel = app.label.substring(0, app.label.indexOf('<span '))
						atomicState.appDisplayName = myLabel
						app.updateLabel(myLabel)
					}
				}
			} else {
            	if (app.label.contains(' (paused)')) {
                	String myLabel = app.label.substring(0, app.label.indexOf(' (paused)'))
                    atomicState.appDisplayName = myLabel
                    app.updateLabel(myLabel)
                } else {
                	atomicState.appDisplayName = app.label
                }
            }
        	if(settings.tempDisable) { 
				paragraph "WARNING: Temporarily Paused - re-enable below." 
			} else { 
				if (settings.theThermostats || !settings.myThermostats) {
					input(name: "theThermostats", type: "${ST?'device.ecobeeSuiteThermostat':'device.EcobeeSuiteThermostat'}", title: "Ecobee Thermostat(s)", 
                    		required: true, multiple: true, submitOnChange: true)
				} else {
            		input(name: "myThermostats", type: "${ST?'device.ecobeeSuiteThermostat':'device.EcobeeSuiteThermostat'}", title: "Ecobee Thermostat(s)", 
                    		required: true, multiple: true, submitOnChange: true)
				}
                if (HE) paragraph ''
            }          
		}
    
		if (!settings.tempDisable && ((settings.theThermostats?.size() > 0) || (settings.myThermostats?.size() > 0))) {

			section(title: (HE?'<b>':'') + "Select HVAC Off Actions" + (HE?'</b>':'')) {
            	paragraph('If you are using  the Quiet Time Helper, you can centralize off/idle actions by turning on Quiet Time from this Helper instead of taking HVAC actions directly. The Quiet Time Helper also offers additional control options.')
                input(name: 'quietTime', type: 'bool', title: 'Enable Quiet Time?', required: true, defaultValue: false, submitOnChange: true)
                if (settings.quietTime) {
                	input(name: 'qtSwitch', type: 'capability.switch', required: true, title: 'Which switch controls Quiet Time?', multiple: false, submitOnChange: true)
                    if (settings.qtSwitch) {
                        input(name: "qtOn", type: "enum", title: "Enable Quiet Time when switch ${settings.qtSwitch.displayName} is:", required: true, multiple: false, options: ["on","off"], submitOnChange: true)
                        if (settings.qtOn != null) paragraph("Switch ${settings.qtSwitch.displayName} will be turned ${settings.qtOn?'On':'Off'} when HVAC Off Actions are taken.")
                    }
                } else {
                	input(name: 'hvacOff', type: "bool", title: "Turn off HVAC?", required: true, defaultValue: true, submitOnChange: true)
                	if ((settings.hvacOff == null) || settings.hvacOff) {
                    	paragraph("HVAC Mode will be set to Off. Circulation, Humidification and/or Dehumidification may still operate while HVAC is Off. Use the Quiet Time Helper for additional control options.")
                    } else {
                    	input(name: 'adjustSetpoints', type: 'bool', title: 'Adjust heat/cool setpoints?', required: true, defaultValue: false, submitOnChange: true)
                        if (adjustSetpoints) {
                        	input(name: 'heatAdjust', type: 'decimal', title: 'Heating setpoint adjustment (+/- 20°)', required: true, defaultValue: 0.0, range: '-20..20')
                            input(name: 'coolAdjust', type: 'decimal', title: 'Cooling setpoint adjustment (+/- 20°)', required: true, defaultValue: 0.0, range: '-20..20')
                        }
                    }
           		}
				paragraph('Note that no HVAC On actions will be taken if the HVAC was already Off when the first contact sensor or switch would have turned it' +
                		  'off; the HVAC will remain Off when all the contacts & switches are reset.')
                if (HE) paragraph ''
            }
            
			section(title: (HE?'<b>':'') + "Select Contact Sensors" + (HE?'</b>':'')) {
				input(name: "contactSensors", title: "Contact Sensors: ", type: "capability.contactSensor", required: false, multiple: true,  submitOnChange: true)
                if (settings.contactSensors) {
                	input(name: 'contactOpen', type: 'bool', title: "Run HVAC Off Actions when ${settings.contactSensors.size()>1?'any of the contacts are':'the contact is'} open?", required: true, defaultValue: true, submitOnChange: true)
                   	paragraph("HVAC Off Actions will be executed when a contact sensor is ${((settings.contactOpen==null)||settings.contactOpen)?'Open':'Closed'}.")
                }
                if (HE) paragraph ''
			}
            
            section(title: (HE?'<b>':'') + "Select Switches" + (HE?'</b>':'')) {
            	input(name: "theSwitches", title: "Switches: ", type: "capability.switch", required: false, multiple: true,  submitOnChange: true)
                if (settings.theSwitches) {
                	input(name: 'switchOn', type: 'bool', title: "Run HVAC Off Actions when ${settings.theSwitches.size()>1?'any of the switches are':'the switch is'} turned on?", required: true, defaultValue: true, submitOnChange: true)
                    paragraph("HVAC Off Actions will be executed when a switch is turned ${((settings.switchOn==null)||settings.switchOn)?'On':'Off'}")
                }
				if (HE) paragraph ''
        	}
            
            if ((settings.contactSensors != null) || (settings.theSwitches != null)) {
				
				section(title: (HE?'<b>':'') + "Timers" + (HE?'</b>':'')) {
					input(name: "offDelay", title: "Delay time (in minutes) before turning off HVAC or Sending Notification [Default=5]", type: "enum", required: true, 
                    	options: ['0', '1', '2', '3', '4', '5', '10', '15', '30'], defaultValue: '5')
					input(name: "onDelay", title: "Delay time (in minutes) before turning HVAC back on  or Sending Notification [Default=0]", type: "enum", required: true, 
                    	options: ['0', '1', '2', '3', '4', '5', '10', '15', '30'], defaultValue: '0')
                    if (HE) paragraph ''
	        	}
            
            	section(title: (HE?'<b>':'') + "Action Preferences" + (HE?'</b>':'')) {
            		input(name: "whichAction", title: "Select which actions to take [Default=Notify Only]", type: "enum", required: true, 
                    	  options: ["Notify Only", "HVAC Actions Only", "Notify and HVAC Actions"], defaultValue: "Notify Only", submitOnChange: true)
					if (settings?.whichAction == null) {app.updateSetting('whichAction', 'Notify Only'); settings?.whichAction = 'Notify Only'; }
                    if (HE) paragraph ''
				}
                        
				if (settings?.whichAction != "HVAC Actions Only") {
					if (ST) {
						section("Notifications") {
							paragraph "A notification will also be sent to the Hello Home log"
							input(name: 'pushNotify', type: 'bool', title: "Send Push notifications to everyone?", defaultValue: false, required: true, submitOnChange: true)
							input(name: "notifiers", type: "capability.notification", title: "Send Notifications to these devices", multiple: true, description: "Select notification devices", 
                            	  required: (!settings.pushNotify && (settings.phone == null) && (!settings.speak || ((settings.musicDevices == null) && (settings.speechDevices == null)))),
								  submitOnChange: true, hideWhenEmpty: true)
							input(name: "phone", type: "text", title: "SMS these numbers (e.g., +15556667777; +441234567890)", 
                            	  required: (!settings.pushNotify && (settings.notifiers == null) && (!settings.speak || ((settings.musicDevices == null) && (settings.speechDevices == null)))),
                                  submitOnChange: true)
                            input(name: "speak", type: "bool", title: "Spoken Notifications?", required: true, defaultValue: false, submitOnChange: true, hideWhenEmpty: (!"speechDevices" && !"musicDevices"))
							if (settings.speak) {
								input(name: "speechDevices", type: "capability.speechSynthesis", title: "Using these speech devices", multiple: true, 
                                	  required: (!settings.pushNotify && (settings.notifiers == null) && (settings.phone == null) && (settings.musicDevices == null)), 
                                      submitOnChange: true, hideWhenEmpty: true)
								input(name: "musicDevices", type: "capability.musicPlayer", title: "Using these music devices", multiple: true, 
                                	  required: (!settings.pushNotify && (settings.notifiers == null) && (settings.phone == null) && (settings.speechDevices == null)), 
                                	  submitOnChange: true, hideWhenEmpty: true)
								if (settings.musicDevices != null) input(name: "volume", type: "number", range: "0..100", title: "At this volume (%)", defaultValue: 50, required: false)
							}
                        	if (!settings.phone && !settings.pushNotify && !settings.speechDevices && !settings.musicDevices && !settings.notifiers) paragraph "WARNING: Notifications configured, but nowhere to send them!\n"
                        }
					} else {		// HE
						section("<b>Use Notification Device(s)</b>") {
							input(name: "notifiers", type: "capability.notification", title: "Send Notifications to these devices", multiple: true, submitOnChange: true,
                            	  required: (!settings.speak || ((settings.musicDevices == null) && (settings.speechDevices == null))))
							paragraph ""
						}
						//section("<b>Use SMS to Phone(s) (limit 10 messages per day)</b>") {
						//	input(name: "phone", type: "text", title: "SMS these numbers (e.g., +15556667777, +441234567890)",
						//		  required: ((settings.notifiers == null) && !settings.speak), submitOnChange: true)
						//	paragraph ""
						//}
						section(hideWhenEmpty: (!"speechDevices" && !"musicDevices"), "<b>Use Speech Device(s)</b>") {
							input(name: "speak", type: "bool", title: "Speak messages?", required: !settings?.notifiers, defaultValue: false, submitOnChange: true)
							if (settings.speak) {
								input(name: "speechDevices", type: "capability.speechSynthesis", required: (settings.musicDevices == null), title: "Using these speech devices", 
                                	  multiple: true, submitOnChange: true)
								input(name: "musicDevices", type: "capability.musicPlayer", required: (settings.speechDevices == null), title: "Using these music devices", 
                                	  multiple: true, submitOnChange: true)
								input(name: "volume", type: "number", range: "0..100", title: "At this volume (%)", defaultValue: 50, required: true)
							}
						}
                        section(){
                        	paragraph "A 'HelloHome' notification will also be sent to the Location Event log"
                        }
					}
            	}
				               
                if (settings?.whichAction?.startsWith('Notify') && (settings?.notifiers || (settings?.speak &&(settings?.speechDevices || settings?.musicDevices)))) {
					section() {
						href name: "customNotifications", title: (HE?'<b>':'') + "Customize Notifications" + (HE?'</b>':''), page: "customNotifications", 
							 description: "Customize notification messages", state: isCustomized()
					}
                }
            }          
		} // End if (theThermostats?.size() > 0)

		section(title: (HE?'<b>':'') + "Temporary Pause" + (HE?'</b>':'')) {
			input(name: "tempDisable", title: "Pause this Helper? ", type: "bool", required: false, description: "", submitOnChange: true)                
        }
		section(title: "") {
			input(name: "debugOff", title: "Disable debug logging? ", type: "bool", required: false, defaultValue: false, submitOnChange: true)
		}
        
        section (getVersionLabel()) {}
    }
	
}
def customNotifications(){
	boolean ST = isST
	boolean HE = !ST
	dynamicPage(name: "customNotifications", uninstall: false, install: false) {
		section((HE?'<b>':'') + "Customize Notifications" + (HE?'</b>':'')) {
			input(name: "customPrefix", type: "enum", title: "Prefix text:", defaultValue: "(helper) at (location):", required: false, submitOnChange: true, 
				  options: ['(helper):', '(helper) at (location):', '(location):', 'none', 'custom'], multiple: false)
			if (settings?.customPrefix == null) { app.updateSetting('customPrefix', '(helper) at (location):'); settings.customPrefix = '(helper) at (location):'; }
			if (settings.customPrefix == 'custom') {
				input(name: "customPrefixText", type: "text", title: "Custom Prefix text", defaultValue: "", required: true, submitOnChange: true)
			}      
			if (settings?.contactSensors) {
				input(name: "customContact", type: "enum", title: "Generally refer to Contact Sensors as", defaultValue: 'contact sensors', submitOnChange: true,
					  options: ['contact sensors', 'doors', 'windows', 'doors and/or windows', 'custom'], multiple: false)
				if (settings?.customContact == null) { app.updateSetting('customContact', 'contact sensors'); settings.customContact = 'contact sensors'; }
				if (settings?.customContact == 'custom') {
					input(name: "customContactText", type: "text", title: "Custom Contact Sensors text", defaultValue: "", required: true, submitOnChange: true)
				}
			}
			if (settings?.theSwitches) {
				input(name: "customSwitch", type: "enum", title: "Generally refer to Switches as", defaultValue: 'switches', submitOnChange: true,
					  options: ['switches', 'toggles', 'custom'], multiple: false)
				if (settings?.customSwitch == null) { app.updateSetting('customSwitch', 'switches'); settings.customSwitch = 'switches'; }
				if (settings.customSwitch == 'custom') {
					input(name: "customSwitchText", type: "text", title: "Custom Switches text", defaultValue: "", required: true, submitOnChange: true)
				}
			}
			input(name: "useSensorNames", type: "bool", title: "Use specific sensor names when possible?", defaultValue: true, submitOnChange: true)
			if (settings?.useSensorNames) {
            	input(name: "addThePrefix", type: "bool", title: "Add 'the' as prefix to device names?", defaultValue: false, submitOnChange: true)
				if (settings?.contactSensors) input(name: "contactCleaners", type: "enum", title: "Strip these words from Contact display names", multiple: true, required: false, 
													submitOnChange: true, options: ['Contact', 'Monitor', 'Multi-Sensor', 'Multisensor', 'Multi', 'Sensor', 'Door', 'Window'])
				if (settings?.theSwitches) input(name: "switchCleaners", type: "enum", title: "Strip these words from Switch display names", multiple: true, required: false, 
												 submitOnChange: true, options: ['Switch', 'Toggle', 'Power', 'Meter'])
			}
			if ((settings?.onDelay != '0') || (settings?.offDelay != '0')) {
				input(name: "includeDelays", type: "bool", title: "Include open/close delay times in notifications?", defaultValue: true, submitOnChange: true)
			}
			input(name: "customTstat", type: "enum", title: "Refer to the HVAC system as", defaultValue: "(thermostat names) is/are", options:
				  ['the HVAC system', '(thermostat names) is/are', 'custom'], submitOnChange: true, multiple: false)
			if (settings?.customTstat == 'custom') {
				input(name: "customTstatText", type: "text", title: "Custom HVAC system text", defaultValue: "", required: true, submitOnChange: true)
			} 
			if (settings?.customTstat == null) { app.updateSetting('customTstat', '(thermostat names) is/are'); settings.customTstat = '(thermostat names) is/are'; }
			if (settings?.customTstat == '(thermostat names) is/are') {
				input(name: "tstatCleaners", type: 'enum', title: "Strip these words from the Thermostat display names", multiple: true, required: false,
					  submitOnChange: true, options: ['EcobeeTherm', 'EcoTherm', 'Thermostat', 'Ecobee'])
				input(name: "tstatPrefix", type: 'enum', title: "Add this prefix to the Thermostat display names", multiple: false, required: false,
					  submitOnChange: true, options: ['the', 'Ecobee', 'Thermostat', 'Ecobee Thermostat', 'the Ecobee', 'the Ecobee Thermostat', 'theThermostat']) 
				input(name: "tstatSuffix", type: 'enum', title: "Add this suffix to the Thermostat display names", multiple: false, required: false,
					  submitOnChange: true, options: ['Ecobee', 'HVAC', 'HVAC system', 'Thermostat', 'heating', 'cooling', 'A/C'])
			}

			paragraph "${HE?'<b><i>':''}Sample notification messages:${HE?'</i></b>':''}"
			String thePrefix = getMsgPrefix()
			String theContact = getMsgContact()
			String theSwitch = getMsgSwitch()
			String theTstat = getMsgTstat()
			String samples = ""
            
			if (settings?.contactSensors) {
				if (!useSensorNames) {
					samples = thePrefix + "one or more " + theContact + " have been ${((settings.contactOpen==null)||settings?.contactOpen)?'open':'closed'} " +
						(((settings?.offDelay != '0') && settings?.includeDelays) ? "for ${offDelay} minute${settings?.offDelay?.toInteger()>1?'s':''}" : 'too long') + ", ${theTstat}now off\n"
				} else {
                	def nameList = []
                	if (settings?.addThePrefix) {
						settings?.contactSensors.each {
							nameList << ('the ' + it.displayName)
                        }
                    } else {
                        nameList = settings?.contactSensors*.displayName
                    }
					String sensorStr = textListToString(nameList)
					if (settings?.contactCleaners != []) {
						settings?.contactCleaners.each{
							sensorStr = sensorStr.replace(it, '').replace(it.toLowerCase(), '').trim()	// Strip out any unnecessary words
						}
					}
					sensorStr = sensorStr.replace(':','').replace('  ', ' ').replace(' ,', ',').trim()           
					samples = thePrefix + sensorStr + " ${settings?.contactSensors.size()>1?'have':'has'} been ${((settings.contactOpen==null)||settings?.contactOpen)?'open':'closed'} " +
								(((settings?.offDelay != '0') && settings?.includeDelays) ? "for ${offDelay} minute${settings?.offDelay?.toInteger()>1?'s':''}" : 'too long') + 
								", ${theTstat} now off\n"
				}
				if ((settings?.onDelay == '0') || !settings?.includeDelays) {
					samples = samples.capitalize() + "${thePrefix}all ${theContact} are ${((settings.contactOpen==null)||settings?.contactOpen)?'closed':'open'}, ${theTstat} now on\n".capitalize()
				} else {
					samples = samples.capitalize() + "${thePrefix}all ${theContact} have been ${((settings.contactOpen==null)||settings?.contactOpen)?'closed':'open'} for ".capitalize() +
						"${settings?.onDelay} minute${settings?.onDelay?.toInteger()>1?'s':''}, ${theTstat} now on\n"
				}
			}
			if (settings?.theSwitches) {
				String switchSample = ""
				if (!useSensorNames) {
					switchSample = thePrefix + "one or more " + theSwitch + " have been ${((settings.switchOn==null)||settings?.switchOn)?'on':'off'} " +
						(((settings?.offDelay != '0') && settings?.includeDelays) ? "for ${offDelay} minute${settings?.offDelay?.toInteger()>1?'s':''}" : 'too long') + ", ${theTstat} now off\n"
				} else {
                	def nameList = []
                	if (settings?.addThePrefix) {
						settings?.theSwitches.each {
							nameList << ('the ' + it.displayName)
                        }
                    } else {
                        nameList = settings?.theSwitches*.displayName
                    }
					String switchStr = textListToString(nameList)
					if (settings?.switchCleaners != []) {
						settings?.switchCleaners.each{
							switchStr = switchStr.replace(it, '').replace(it.toLowerCase(), '').trim()	// Strip out any unnecessary words
						}
					}
					switchStr = switchStr.replace(':', '').replace('  ', ' ').replace(' ,', ',').trim()
					switchSample = thePrefix + switchStr + " ${settings?.theSwitches.size()>1?'have':'has'} been ${((settings.switchOn==null)||settings?.switchOn)?'on':'off'} " +
								(((settings?.offDelay != '0') && settings?.includeDelays) ? "for ${offDelay} minute${settings?.offDelay?.toInteger()>1?'s':''}" : 'too long') + 
								", ${theTstat} now off\n"
				}
				if ((settings?.onDelay == '0') || !settings?.includeDelays) {
					samples = samples + switchSample.capitalize() + "${thePrefix}all ${theSwitch} are ${((settings.contactOpen==null)||settings?.switchOn)?'off':'on'}, ${theTstat} now on\n".capitalize()
				} else {
					samples = samples + switchSample.capitalize() + "${thePrefix}all ${theSwitch} have been ${((settings.contactOpen==null)||settings?.switchOn)?'off':'on'} for ".capitalize() +
						"${settings?.onDelay} minute${settings?.onDelay?.toInteger()>1?'s':''}, ${theTstat} now on\n"
				}
			}
			paragraph samples
		}
	}
}
def isCustomized() {
	return (customPrefix || customContact || customSwitch || customTstat || (includeDelays != null) || (useSensorNames != null)) ? "complete" : null
}

// Main functions
void installed() {
	LOG("Installed with settings ${settings}", 4, null, 'trace')
	initialize()  
}
void uninstalled () {
   clearReservations()
}
void updated() {
	LOG("Updated with settings ${settings}", 4, null, 'trace')
	unsubscribe()
	unschedule()
	initialize()
    // tester()
}

//
// TODO - if stat goes offline, then comes back online, then re-initialize states...
//
def initialize() {
	LOG("${getVersionLabel()} - Initializing...", 2, "", 'info')
	updateMyLabel()
    boolean ST = atomicState.isST
    boolean HE = !ST
    //log.debug "settings: ${settings}"
	
	if(settings.tempDisable == true) {
    	clearReservations()
		LOG("Temporarily Paused", 4, null, 'info')
		return true
	}
	if (settings.debugOff) log.info "log.debug() logging disabled"
    // subscribe(app, appTouch)
	
	boolean contactOffState = false
	if (contactSensors) {
    	def openSensors = 0
        def closedSensors = 0
        contactSensors.each {
        	def currentContact = ST ? it?.currentValue('contact') : it?.currentValue('contact', true)	// bypass the cache on Hubitat
            if (currentContact == 'open') { openSensors++; } else { closedSensors++; }
        }
    	if (contactOpen) {
        	subscribe(contactSensors, "contact.open", sensorOpened)
            subscribe(contactSensors, "contact.closed", sensorClosed)
        	contactOffState = (openSensors != 0) 		// true if any of the sensors are currently open
       	} else {
        	subscribe(contactSensors, "contact.closed", sensorOpened)
			subscribe(contactSensors, "contact.open", sensorClosed)
			contactOffState = (closedSensors != 0)
        }
    }
    LOG("contactOffState = ${contactOffState}",4,null,'info')
    
    boolean switchOffState = false
    if (theSwitches) {
    	def onSwitches = 0
        def offSwitches = 0
        theSwitches.each {
        def currentSwitch = ST ? it.currentValue('switch') : it.currentValue('switch', true)
        	if (currentSwitch == 'on') { onSwitches++ } else { offSwitches++ }
        }
    	if (switchOn) {
        	subscribe(theSwitches, "switch.on", sensorOpened)
            subscribe(theSwitches, "switch.off", sensorClosed)
            switchOffState = (onSwitches != 0)
        } else {
        	subscribe(theSwitches, "switch.off", sensorOpened)
            subscribe(theSwitches, "switch.on", sensorClosed)
			switchOffState = (offSwitches != 0)
        }
    }
    LOG("switchOffState = ${switchOffState}",4,null,'info')
    
    //def tempState = atomicState.HVACModeState
    //if (tempState == null) tempState = (contactOffState || switchOffState)?'off':'on'		// recalculate if we should be off or on
    def tempState = (contactOffState || switchOffState) ? 'off' : 'on'		// recalculate if we should be off or on
	def theStats = settings.theThermostats ? settings.theThermostats : settings.myThermostats
	def tmpThermSavedState = atomicState.thermSavedState ?: [:]
    if (tempState == 'on') {
		if (atomicState.HVACModeState != 'on') turnOnHVAC(true)
    	// Initialize the saved state values
    	if (!settings.quietTime) {  		
    		theStats.each() { therm ->
    			def tid = getDeviceId(therm.deviceNetworkId)
				if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
				if (ST) {
					tmpThermSavedState[tid] = [	mode: therm.currentValue('thermostatMode'), HVACModeState: 'on', ]
					if (settings.adjustSetpoints) {
						tmpThermSavedState[tid] += [
														heatSP: therm.currentValue('heatingSetpoint'), 
														coolSP: therm.currentValue('coolingSetpoint'),
														heatAdj: 999.0,
														coolAdj: 999.0,
														holdType: therm.currentValue('lastHoldType'),
														thermostatHold: therm.currentValue('thermostatHold'),
														currentProgramName: therm.currentValue('currentProgramName'),	// Hold: Home
														currentProgramId: therm.currentValue('currentProgramId'),		// home
														currentProgram: therm.currentValue('currentProgram'),			// Home
												  ]
					}
				} else {
					// We have to ensure we get the latest values on HE - it caches stuff when it probably shouldn't
					tmpThermSavedState[tid] = [	mode: therm.currentValue('thermostatMode', true), HVACModeState: 'on' ]
					if (settings.adjustSetpoints) {
						tmpThermSavedState[tid] += [
														heatSP: therm.currentValue('heatingSetpoint', true), 
														coolSP: therm.currentValue('coolingSetpoint', true),
														heatAdj: 999.0,
														coolAdj: 999.0,
														holdType: therm.currentValue('lastHoldType', true),
														thermostatHold: therm.currentValue('thermostatHold', true),
														currentProgramName: therm.currentValue('currentProgramName', true),	// Hold: Home
														currentProgramId: therm.currentValue('currentProgramId', true),		// home
														currentProgram: therm.currentValue('currentProgram', true),			// Home
												  ]
					}
				}
    		}
    	}
        
    } else {
    	LOG("Initialized while should be 'Off' - can't update states",2,null,'warn')
        theStats.each() { therm ->
    		def tid = getDeviceId(therm.deviceNetworkId)
            if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
            tmpThermSavedState[tid].mode = (atomicState.isST ? therm.currentValue('thermostatMode') : therm.currentValue('thermostatMode', true))
            tmpThermSavedState[tid].HVACModeState = 'off'
        }
        if (atomicState.HVACModeState != 'off') turnOffHVAC(true)
    }
    
	if (!settings.quietTime) {
    	//if ((settings.hvacOff == null) || settings.hvacOff) 
		subscribe(theStats, 'thermostatMode', statModeChange)
        //else 
		if (settings.adjustSetpoints) {
    		subscribe(theStats, 'heatingSetpoint', heatSPHandler)
            subscribe(theStats, 'coolingSetpoint', coolSPHandler)
        }
    }
    atomicState.thermSavedState = tmpThermSavedState
	LOG("initialize() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
	LOG("initialize() exiting",4,null,'trace')
}

def statModeChange(evt) {
	// only gets called if we are turning off the HVAC (not for quietTime or setpointAdjust operations)
    def tid = getDeviceId(evt.device.deviceNetworkId)
    def tmpThermSavedState = atomicState.thermSavedState ?: [:]
    if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
    
	if (evt.value == 'off') {
		if (atomicState.HVACModeState != 'off') {	// If this is our generated mode change to 'off', then the HVACModeState should already be 'off'
			atomicState.HVACModeState = 'off'
			tmpThermSavedState[tid].mode = 'off'	// update the saved mode
		}
        tmpThermSavedState[tid].HVACModeState = 'off'
		tmpThermSavedState[tid].wasAlreadyOff = false
    } else {
    	// somebody has overridden us..
        cancelReservation( tid, 'modeOff' )		// might as well give up our reservation
    	if (atomicState.HVACModeState != 'on') atomicState.HVACModeState = 'on'
        tmpThermSavedState[tid].HVACModeState = 'on'
		tmpThermSavedState[tid].wasAlreadyOff = false
		tmpThermSavedState[tid].mode = evt.value	// update the saved mode
    }
    atomicState.thermSavedState = tmpThermSavedState
	LOG("statModeChange() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
}
    
def heatSPHandler( evt ) {
	// called when the heatingSetpoint value changes, but only if we are monitoring/making setpoint changes
	// (ie., this won't get called if we are using Quiet Time or just HVAC Off)
    if (evt.numberValue != null) {
		def tid = getDeviceId(evt.device.deviceNetworkId)
    
    	// save the new value
		def tmpThermSavedState = atomicState.thermSavedState ?: [:]
        if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
    	if ((tmpThermSavedState[tid].containsKey(heatAdj)) && (tmpThermSavedState[tid].heatAdj == evt.numberValue)) return 	// we generated this event (below)
    	tmpThermSavedState[tid].heatSP = evt.numberValue
    
    	// if (!atomicState.HVACModeState.contains('off')) {			// Only adjust setpoints when the HVAC is not off
        if (tmpThermSavedState[tid].HVACModeState != 'off') {			// Only adjust setpoints when the HVAC is not off
        	def h = evt.numberValue + settings.heatAdjust
        	tmpThermSavedState[tid].heatAdj = h
        	evt.device.setHeatingSetpoint( h, 'nextTransition')
        	// Notify???
    	} else {
    		tmpThermSavedState[tid].heatAdj = 999.0
    	}
    	atomicState.thermSavedState = tmpThermSavedState
    }
	LOG("heatSPHandler() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
}

def coolSPHandler( evt ) {
	if (evt.numberValue != null) {
        def tid = getDeviceId(evt.device.deviceNetworkId)

        // save the new value
        def tmpThermSavedState = atomicState.thermSavedState ?: [:]
        if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
        if ((tmpThermSavedState[tid].containsKey('coolAdj')) && (tmpThermSavedState[tid].coolAdj == evt.numberValue)) return
        tmpThermSavedState[tid].coolSP = evt.numberValue

        //if (!atomicState.HVACModeState.contains('off')) {
        if (tmpThermSavedState[tid].HVACModeState != 'off') {
            // adjust and change the actual heating setpoints
            def c = evt.numberValue + settings.coolAdjust
            tmpThermSavedState[tid].coolAdj = c
            evt.device.setCoolingSetpoint( c, 'nextTransition')
            // Notify?
        } else {
            tmpThermSavedState = 999.0
        }
        atomicState.thermSavedState = tmpThermSavedState
    }
	LOG("coolSPHandler() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
}

// "sensorOpened" called when state change should turn HVAC off - routine name preserved for backwards compatibility with prior implementations
void sensorOpened(evt=null) {
	LOG("sensorOpened() - ${evt?.device} ${evt?.name} ${evt?.value}", 4, null, 'trace')
    def HVACModeState = atomicState.HVACModeState
    if (HVACModeState == 'off_pending') return		// already in process of turning off

	boolean ST = atomicState.isST
	def tmpThermSavedState = atomicState.thermSavedState ?: [:]
	
	def theStats = settings.theThermostats ? settings.theThermostats : settings.myThermostats
    if (HVACModeState == 'off') { // || (HVACModeState == 'off_pending')) {
    	// HVAC is already off
		def tstatModes = ST ? theStats*.currentValue('thermostatMode') : theStats*.currentValue('thermostatMode', true)
		if (tstatModes.contains('off')) { // at least 1 thermostat is actually off	
			if (numOpen() == 1) {
				atomicState.wasAlreadyOff = true
				theStats.each { therm ->
					def tid = getDeviceId(therm.deviceNetworkId)
					if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
					def statMode = ST ? therm.currentValue('thermostatMode') : therm.currentValue('thermostatMode', true)
					tmpThermSavedState[tid].wasAlreadyOff = (statMode == 'off') 
				}
			}
			atomicState.thermSavedState = tmpThermSavedState
			LOG("sensorOpened(already off) - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
        	return
		} else {
			// No stats are actually off, clean up the mess
			if (HVACModeState != 'on_pending') HVACModeState = 'on'
			atomicState.HVACModeState = HVACModeState
			atomicState.wasAlreadyOff = false
			theStats.each { therm ->
				def tid = getDeviceId(therm.deviceNetworkId)
				if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
				tmpThermSavedState[tid].wasAlreadyOff = false
			}
			atomicState.thermSavedState = tmpThermSavedState
		}
		LOG("sensorOpened() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
    }
    Integer delay = (settings.onDelay ?: 0) as Integer
    if (HVACModeState == 'on_pending') {
		// HVAC is already/still off
        // if (delay > 0) 
		unschedule(turnOnHVAC)
		atomicState.HVACModeState = 'off'
		turnOffHVAC(true)			// Make sure they are really off
        return
    }

	// HVAC is on, turn it off
   	atomicState.HVACModeState = 'off_pending'
	delay = (settings.offDelay ?: 5) as Integer
	if (delay > 0) { 
		runIn(delay*60, 'turnOffHVAC', [overwrite: true])
		LOG("${theStats.toString()[1..-2]} will be turned off in ${delay} minutes", 4, null, 'info')
	} else { turnOffHVAC(false) }  
}

//void openedScheduledActions() {		// preserved only for backwards compatibility
//	LOG("openedScheduledActions entered", 5)
//    turnOffHVAC(false)
//}

// "sensorClosed" called when state change should turn HVAC On (routine name preserved for backwards compatibility with prior implementations)
void sensorClosed(evt=null) {
	// Turn HVAC Off action has occured
    LOG("sensorClosed() - ${evt?.device} ${evt?.name} ${evt?.value}", 4, null,'trace')
	def HVACModeState = atomicState.HVACModeState
    boolean ST = atomicState.isST
    
    if (allClosed() == true) {
    	if (HVACModeState == 'on_pending') return
        
		if (atomicState.wasAlreadyOff == true) {
			def tmpThermSavedState = atomicState.thermSavedState ?: [:]
			int i = 0, j = 0
			def theStats = settings.theThermostats ? settings.theThermostats : settings.myThermostats
			theStats.each { therm ->
				j++
				def tid = getDeviceId(therm.deviceNetworkId)
				if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
				def statMode = ST ? therm.currentValue('thermostatMode') : therm.currentValue('thermostatMode', true)
				if (tmpThermSavedState[tid].containsKey('wasAlreadyOff') && (tmpThermSavedState[tid].wasAlreadyOff == true)) i++
			}
			if (i == j) {
				atomicState.wasAlreadyOff = false
				LOG("All sensors & switches are reset, but HVAC was already off when the first ${settings.contactSensors?'contact':''} " +
					"${(settings.contactSensors && settings.theSwitches)?'or ':''}${settings.theSwitches?'switch':''} was " +
					"${(settings.contactSensors && settings.contactOpen)?'opened':''}${(settings.contactSensors && !settings.contactOpen)?'closed':''}" +
					"${(settings.contactSensors && settings.theSwitches)?'/':''}" +
					"${(settings.theSwitches && settings.switchOn)?'turned on':''}${(settings.theSwitches && !settings.switchOn)?'turned off':''}, no action taken.",
					3, null, 'info')
				return
			}
		}
		if (HVACModeState == 'on') {
			LOG("All sensors & switches are reset, and HVAC is already on", 3, null, 'info')
			turnOnHVAC(true)	// Just in case
			return	// already on, nothing more to do (catches 'on' and 'on_pending')
		}
        Integer delay = (settings.offDelay?: 5) as Integer
		if (HVACModeState == 'off_pending' ) {
			unschedule(turnOffHVAC)
			atomicState.HVACModeState = 'on'
			LOG("All sensors & switches are reset, off_pending was cancelled", 3, null, 'info')
            // still on
			turnOnHVAC(true)	// Just in case
            return
        }
	    
        LOG("All Contact Sensors & Switches are reset, initiating actions.", 3,null,'trace')		
        
        atomicState.HVACModeState = 'on_pending'
		// unschedule(openedScheduledActions)
		unschedule(turnOffHVAC)
	    delay = (settings.onDelay?: 0) as Integer
    	//LOG("The on delay is ${delay}",5,null,'info')
		if (delay > 0) { 
			runIn(delay*60, 'turnOnHVAC', [overwrite: true]) 
			LOG("${theStats.toString()[1..-2]} will be turned on in ${delay} minutes", 3, null, 'info')
		} else { turnOnHVAC(false) }
	} else {
    	LOG("No action to perform yet...", 5,null,'trace')
    }
}

// void closedScheduledActions() {
//	LOG("closedScheduledActions entered", 5)
//	turnOnHVAC(false)
//}

void turnOffHVAC(quietly = false) {
	// Save current states
    LOG("turnoffHVAC(quietly=${quietly}) entered...", 4,null,'trace')
	boolean ST = atomicState.isST
	
    if (allClosed() && (atomicState.HVACModeState == 'off_pending')) {
    	// Nothing is open, maybe got closed while we were waiting, abort the "off"
        LOG("turnOffHVAC() called, but everything is closed/off, ignoring request & leaving HVAC on",3,null,warn)
        atomicState.HVACModeState = 'on'
        return
    }
    
    atomicState.HVACModeState = 'off'
    def action = settings.whichAction ? settings.whichAction :'Notify Only'
    def tmpThermSavedState = atomicState.thermSavedState ?: [:]
    def tstatNames = []
    boolean doHVAC = action.contains('HVAC')
    def theStats = settings.theThermostats ? settings.theThermostats : settings.myThermostats
	// log.debug "theStats: ${theStats}"
	theStats.each() { therm ->
    	def tid = getDeviceId(therm.deviceNetworkId)
        if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
        if( doHVAC ) {
        	if (settings.quietTime) {
            	// Turn on quiet time
                qtSwitch."${settings.qtOn}"()
                LOG("${therm.displayName} Quiet Time enabled (${qtSwitch.displayName} turned ${settings.qtOn})",3,null,'info')
            } else if ((settings.hvacOff == null) || settings.hvacOff) {
            	// turn off the HVAC
                makeReservation(tid, 'modeOff')						// make sure nobody else turns HVAC on until I'm ready
				def thermostatMode = ST ? therm.currentValue('thermostatMode') : therm.currentValue('thermostatMode', true)
    			if (thermostatMode != 'off') {
                	tmpThermSavedState[tid].mode = thermostatMode	// therm.currentValue('thermostatMode')
                    tmpThermSavedState[tid].HVACModeState = 'off'
					tmpThermSavedState[tid].wasAlreadyOff = false
            		therm.setThermostatMode('off')
                	if (!tstatNames.contains(therm.displayName)) tstatNames << therm.displayName		// only report the ones that aren't off already
                	LOG("${therm.displayName} turned off (was ${tmpThermSavedState[tid].mode})",3,null,'info')    
            	}
            } else if (settings.adjustSetpoints) {
            	// Adjust the setpoints
                def h = ST ? therm.currentValue('heatingSetpoint') : therm.currentValue('heatingSetpoint', true)
                def c = ST ? therm.currentValue('coolingSetpoint') : therm.currentValue('coolingSetpoint', true)
                // save the current values for when we turn back on
                tmpThermSavedState[tid].heatSP = h
                tmpThermSavedState[tid].coolSP = c
                h = h + settings.heatAdjust
                c = c + settings.coolAdjust
                tmpThermSavedState[tid].heatAdj = h
                tmpThermSavedState[tid].coolAdj = c
                
				if (ST) {
					tmpThermSavedState[tid].holdType = therm.currentValue('lastHoldType')
					tmpThermSavedState[tid].thermostatHold = therm.currentValue('thermostatHold')
					tmpThermSavedState[tid].currentProgramName = therm.currentValue('currentProgramName')	// Hold: Home, Vacation
					tmpThermSavedState[tid].currentProgramId = therm.currentValue('currentProgramId')		// home
					tmpThermSavedState[tid].currentProgram = therm.currentValue('currentProgram')			// Home
					tmpThermSavedState[tid].scheduledProgram = therm.currentValue('scheduledProgram')
				} else {
					tmpThermSavedState[tid].holdType = therm.currentValue('lastHoldType', true)
					tmpThermSavedState[tid].thermostatHold = therm.currentValue('thermostatHold', true)
					tmpThermSavedState[tid].currentProgramName = therm.currentValue('currentProgramName', true)	// Hold: Home, Vacation
					tmpThermSavedState[tid].currentProgramId = therm.currentValue('currentProgramId', true)		// home
					tmpThermSavedState[tid].currentProgram = therm.currentValue('currentProgram', true)			// Home
					tmpThermSavedState[tid].scheduledProgram = therm.currentValue('scheduledProgram', true)
				}
                therm.setHeatingSetpoint(h, 'nextTransition')
                therm.setCoolingSetpoint(c, 'nextTransition')
                LOG("${therm.displayName} heatingSetpoint adjusted to ${h}, coolingSetpoint to ${c}",3,null,'info')
            }
        } else {
        	if (tmpThermSavedState[tid].mode != 'off') {
                tstatNames << therm.displayName		// only report the ones that aren't off
        		LOG("Saved ${therm.displayName}'s current mode (${tmpThermSavedState[tid].mode})",3,null,'info')
            }
        }
    }
    atomicState.thermSavedState = tmpThermSavedState
	LOG("turnOffHVAC() - thermSavedState: ${atomicState.thermSavedState}", 4, null, 'debug')
    
	if (tstatNames.size() > 0) {
    	if (action.contains('Notify')  && !quietly) {
    		boolean notified = false
			def tstatModes = ST ? theStats*.currentValue('thermostatMode') : theStats*.currentValue(thermostatMode, true)
			boolean isOn = tstatModes.contains('auto') || tstatModes.contains('heat') || tstatModes.contains('cool')
        	Integer delay = (settings.offDelay != null ? settings.offDelay : 5) as Integer
			String theStatsStr = getMsgTstats()
			String justTheStats = theStatsStr.endsWith(' is') ? theStatsStr[0..-3] : (theStatsStr.endsWith(' are') ? theStatsStr[0..-4] : theStatsStr)

			if (contactSensors) {
        		def sensorList = []
            	contactSensors.each { 
                def currentContact = ST ? it.currentValue('contact') : it.currentValue('contact', true)
            		if (currentContact == (settings.contactOpen?'open':'closed')) sensorList << it
            	}
				String message = ""
				if (!settings?.useSensorNames) {
                	message = message + "${sensorList.size()>1?'some':'one'} of the ${getMsgContact()} ${sensorList.size()>1?'have':'has'} been "
				} else {
                	def nameList = []
                	if (settings?.addThePrefix) {
						sensorList.each {
							nameList << ('the ' + it.displayName)
                        }
                    } else {
                        nameList = sensorList*.displayName
                    }
					String sensorStr = textListToString(nameList)
					if (contactCleaners != []) {
						contactCleaners.each{
							sensorStr = sensorStr.replace(it, '').replace(it.toLowerCase(), '').trim()	// Strip out any unnecessary words
						}
					}
					sensorStr = sensorStr.replace(':','').replace('  ', ' ').replace(' ,', ',').trim()
					message = message + "${sensorStr} ${(sensorNames.size()>1)?'has':'have'} been "
				}
				
				if (delay == 0) {
					message = message + "${contactOpen?'opened':'closed'}, "
				} else if ((delay != 0) && settings?.includeDelays) {
					message = message + "${contactOpen?'open':'closed'} for ${delay} minute${delay>1?'s':''}, "
            	} else {
            		message = message + "${contactOpen?'open':'closed'} for too long, "
            	}
				message = message + (doHVAC ? (theStatsStr + ' now off') : ('you should turn off ' + justTheStats))
				sendMessage(message)
            	notified = true		// only send 1 notification
        	}
        	if (!notified && theSwitches) {
        		def switchList = []
            	theSwitches.each {
                	def currentSwitch = ST ? it.currentValue('switch') : it.currentValue('switch', true)
            		if (currentSwitch == (switchOn?'on':'off')) switchList << it
            	}
				if (!settings?.useSensorNames) {
                    message = message + "${switchList.size()>1?'some':'one'} of the ${getMsgSwitch()} ${switchList.size()>1?'have':'has'} been "
				} else {
                	def nameList = []
                	if (settings?.addThePrefix) {
						switchList.each {
							nameList << ('the ' + it.displayName)
                        }
                    } else {
                        nameList = switchList*.displayName
                    }
					String switchStr = textListToString(nameList)
					if (switchCleaners != []) {
						switchCleaners.each{
							switchStr = switchStr.replace(it, '').replace(it.toLowerCase(), '').trim()	// Strip out any unnecessary words
						}
					}
					switchStr = switchStr.replace(':', '').replace('  ', ' ').replace(' ,', ',').trim()
					message = message + "${switchStr} ${(switchNames.size()>1)?'has':'have'} been "
				}
				if (delay == 0) {
					message = message + "turned ${switchOn?'on':'off'}, "
				} else if ((delay != 0) && settings?.includeDelays) {
    				message = message + "${switchOn?'on':'off'} for ${delay} minute${delay>1?'s':''}, "
            	} else {
            		message = message + "${switchOn?'on':'off'} for too long, "
            	}
				message = message + (doHVAC ? (theStatsStr + ' now off') : ('you should turn off ' + justTheStats))
				sendMessage(message)
          		notified = true
        	}
        	if (notified) LOG('Notifications sent',3,null,'info')
    	}
    } else {
    	if (action.contains('Notify') && !quietly) {
        	sendMessage("${theStats} already off")
            LOG('All thermostats are already off',3,null,'info')
        }
    }
}

void turnOnHVAC(quietly = false) {
	// Restore previous state
    LOG("turnOnHVAC(quietly=${quietly}) entered...", 4,null,'trace')
	boolean ST = atomicState.isST
    
	if (!allClosed() && (atomicState.HVACModeState == 'on_pending')) {
    	LOG("turnOnHVAC() called, but somethings is still open/on, ignoring request & leaving HVAC off",3,null,warn)
    	atomicState.HVACModeState = 'off'
    	return
    }
    
    atomicState.HVACModeState = 'on'
    String action = settings.whichAction ?: 'Notify Only'
    boolean doHVAC = action.contains('HVAC')
	LOG("turnOnHVAC() - action: ${action}, doHVAC: ${doHVAC}", 3, null, 'info')
    boolean notReserved = true
	def theStats = settings.theThermostats ? settings.theThermostats : settings.myThermostats
	def tstatNames = []
	// log.debug "turnOnHVAC() - theStats: ${theStats}"
	def tmpThermSavedState = atomicState.thermSavedState ?: [:]
	LOG("turnOnHVAC() - thermSavedState = ${atomicState.thermSavedState}", 3, null, 'debug')
    if (doHVAC) {
        theStats.each { therm ->
			LOG("Working on thermostat: ${therm}", 3, null, 'info')
            // if (!tstatNames.contains(therm.displayName)) tstatNames << therm.displayName
            def tid = getDeviceId(therm.deviceNetworkId)
            if (!tmpThermSavedState || !tmpThermSavedState[tid]) tmpThermSavedState[tid] = [:]
            
            String priorMode = settings.defaultMode
            
           //if (tmpThermSavedState?.containsKey(tid)) {
            	if (settings.quietTime) {
            		// Turn on quiet time
            		def onOff = settings.qtOn=='on' ? 'off' : 'on'
                	qtSwitch."$onOff"()
                    LOG("${therm.displayName} Quiet Time disabled (${qtSwitch.displayName} turned ${onOff})",3,null,'info')
            	} else if ((settings.hvacOff == null) || settings.hvacOff) {
            		// turn on the HVAC
                    def oldMode = ST ? therm.currentValue('thermostatMode') : therm.currentValue('thermostatMode', true) 
                    def newMode = tmpThermSavedState[tid]?.mode ?: 'auto'
					LOG("Current HVAC mode: ${oldMode}, desired HVAC mode: ${newMode}", 3, null, 'info')
                    if (newMode != oldMode) {
                    	def i = countReservations( tid, 'modeOff' ) - (haveReservation(tid, 'modeOff') ? 1 : 0)
                        // log.debug "count=${countReservations(tid,'modeOff')}, have=${haveReservation(tid,'modeOff')}, i=${i}"
                    	if ((oldMode == 'off') && (i > 0)) {
                        	// Currently off, and somebody besides me has a reservation - just release my reservation
                            cancelReservation(tid, 'modeOff')
                            notReserved = false							
                            LOG("Cannot change ${therm.displayName} to ${newMode.capitalize()} - ${getGuestList(tid, 'modeOff').toString()[1..-2]} hold 'modeOff' reservations",1,null,'warn')
                        } else {
                        	// Not off, or nobody else but me has a reservation
                            cancelReservation(tid, 'modeOff')
							if (tmpThermSavedState[tid].containsKey('wasAlreadyOff') && (tmpThermSavedState[tid].wasAlreadyOff == false)) {
								tmpThermSavedState[tid].HVACModeState = 'on'
								tmpThermSavedState[tid].mode = newMode
                        		therm.setThermostatMode( newMode )                            
                				if (!tstatNames.contains(therm.displayName)) tstatNames << therm.displayName		// only report the ones that aren't off already
                				LOG("${therm.displayName} ${newMode.capitalize()} Mode restored (was ${oldMode.capitalize()})",3,null,'info')
							} else {
								LOG("${therm.displayName} was already off, not turning back on",3,null,'info')
							}
                        } 
                    } else {
                    	LOG("${therm.displayName} is already in ${newMode.capitalize()}",3,null,'info')
						tmpThermSavedState[tid].HVACModeState = 'on'
                    }
            	} else if (settings.adjustSetpoints) {
                	// Restore the prior values
                    tmpThermSavedState[tid].HVACModeState = 'on'
                    def holdType = tmpThermSavedState[tid].holdType
                    if (holdType == '') holdType = 'nextTransition'
                    if (tmpThermSavedState[tid].currentProgram == tmpThermSavedState.scheduledProgram) {
                       	// we were running the scheuled program when we turned off - just return to the currently scheduled program
                        therm.resumeProgram()
                        LOG("${therm.displayName} resumed current program",3,null,'info')
                    } else if (tmpThermSavedState[tid].currentProgramName == ('Hold: ' + tmpThermSavedState[tid].currentProgram)) {
                    	// we were in a hold of a named program - reinstate the hold IF the saved scheduledProgram == the current scheduledProgram
						def scheduledProgram = ST ? therm.currentValue('scheduledProgram') : therm.currentValue('scheduledProgram', true)
                        if (tmpThermSavedState[tid].scheduledProgram == scheduledProgram) {
                        	therm.setThermostatProgram(tmpThermSavedState[tid].currentProgram, holdType)
                        	LOG("${therm.displayName} returned to ${tmpThermSavedState[tid]} program (${holdType})",3,null,'info')
                        }
                    } else {
                    	// we were in some other sort of hold - so set a new hold to the original values
                		def h = tmpThermSavedState[tid].heatSP
                		def c = tmpThermSavedState[tid].coolSP
                		tmpThermSavedState[tid].heatAdj = 999.0
                		tmpThermSavedState[tid].coolAdj = 999.0
                		therm.setHeatingSetpoint(h, holdType) // should probably be the current holdType
                		therm.setCoolingSetpoint(c, holdType)
                    	LOG("${therm.displayName} heatingSetpoint returned to ${h}, coolingSetpoint to ${c} (${holdType})",3,null,'info')
                    }
            	}
            //}
		} 
	}
    atomicState.thermSavedState = tmpThermSavedState
	LOG("turnOnHVAC() - thermSavedState: ${atomicState.thermSavedState}",4,null,'debug')
    
    if ( action.contains('Notify') && !quietly ) {
		if (!doHVAC && (tstatNames == [])) tstatNames = theStats*.displayName
    	boolean notified = false
        Integer delay = (settings.onDelay != null ? settings.onDelay : 0) as Integer
		def tstatModes = ST ? theStats*.currentValue('thermostatMode') : theStats*.currentValue('thermostatMode', true)
		def isOff = tstatModes?.contains('off')
		String theStatsStr = getMsgTstats()
		String justTheStats = theStatsStr.endsWith(' is') ? theStatsStr[0..-3] : (theStatsStr.endsWith(' are') ? theStatsStr[0..-4] : theStatsStr)
        String message = ""
    	if (contactSensors) {
            message =  "${contactSensors.size()>1?'all of':''} the ${getMsgContact()} have been "
            if (delay == 0) {
                message = message + "${contactOpen?'closed':'opened'}, "
            } else if ((delay != 0) && settings?.includeDelays) {
                message = message + "${contactOpen?'closed':'open'} for ${delay} minute${delay>1?'s':''}, "
            } else {
                message = message + "${contactOpen?'closed':'opened'}, "
            }
            notified = true
        }
        if (theSwitches) {
        	if (notified) message = message + ' and '
            message = message + "${theSwitches.size()>1?'all of':''} the ${getMsgSwitch()} have been "
            if (delay == 0) {
				message = message + "turned ${switchOn?'off':'on'}, "
			} else if ((delay != 0) && settings?.includeDelays) {
				message = message + "${switchOn?'off':'on'} for ${delay} minute${delay>1?'s':''}, "
			} else {
				message = message + "turned ${switchOn?'off':'on'}, "
			}
        }
		message = message + (doHVAC ? (notReserved ? (theStatsStr + ' now on') : ('but reservations block turning on ' + justTheStats)) : ('you could turn on ' + justTheStats))
		sendMessage(message)
        LOG('Notifications sent',3,null,'info')
    }    
}

boolean allClosed() {
	boolean ST = atomicState.isST
	// Check if all Sensors are in "HVAC ON" state   
    def response = true
    String txt = ''
    if (contactSensors) {
    	if (contactOpen) {
        	txt = 'closed'
            def currentContacts = ST ? contactSensors*.currentContact : contactSensors*.currentValue('contact', true)
        	if (currentContacts.contains('open')) response = false
        } else {
        	txt = 'open'
        	def currentContacts = ST ? contactSensors*.currentContact : contactSensors*.currentValue('contact', true)
        	if (currentContacts.contains('closed')) response = false
        }
        if (response) LOG("All contact sensors are ${txt}",3,null,'info')
    }
    txt = ''
    if (response && theSwitches) {
    	if (switchOn) {
        	txt = 'off'
        	def currentSwitches = ST ? theSwitches*.currentSwitch : theSwitches*.currentValue('switch', true)
        	if (currentSwitches.contains('on')) response = false
        } else {
        	txt = 'on'
        	def currentSwitches = ST ? theSwitches*.currentSwitch : theSwitches*.currentValue('switch', true)
        	if (currentSwitches.contains('off')) response = false
        }
        if (response) LOG("All switches are ${txt}",3,null,'info')
    }
    LOG("allClosed(): ${response}",4,null,'info')
    return response
}

def numOpen() {
	def response = 0
	if (settings.contactSensors) {
		if (settings.contactOpen) {
			def currentContacts = ST ? contactSensors*.currentContact : contactSensors*.currentValue('contact', true)
        	response = currentContacts.count { it == 'open' }
		} else {
			def currentContacts = ST ? contactSensors*.currentContact : contactSensors*.currentValue('contact', true)
        	response = currentContacts.count { it == 'closed' }
		}
	}
	if (settings.theSwitches) {
		if ( settings.switchOn ) {
        	def currentSwitches = ST ? theSwitches*.currentSwitch : theSwitches*.currentValue('switch', true)
			response += currentSwitches.count { it == 'on' }
		} else {
			def currentSwitches = ST ? theSwitches*.currentSwitch : theSwitches*.currentValue('switch', true)
			response += currentSwitches.count { it == 'off' }
		}
	}
    LOG("numOpen(): ${response}",3,null,'info')
	return response
}

// Helper functions
String getDeviceId(networkId) {
    return networkId.split(/\./).last()
}
// Reservation Management Functions - Now implemented in Ecobee Suite Manager
void makeReservation(String tid, String type='modeOff' ) {
	parent.makeReservation( tid, app.id as String, type )
}
// Cancel my reservation
void cancelReservation(String tid, String type='modeOff') {
	//log.debug "cancel ${tid}, ${type}"
	parent.cancelReservation( tid, app.id as String, type )
}
// Do I have a reservation?
Boolean haveReservation(String tid, String type='modeOff') {
	return parent.haveReservation( tid, app.id as String, type )
}
// Do any Apps have reservations?
Boolean anyReservations(String tid, String type='modeOff') {
	return parent.anyReservations( tid, type )
}
// How many apps have reservations?
Integer countReservations(String tid, String type='modeOff') {
	return parent.countReservations( tid, type )
}
// Get the list of app IDs that have reservations
List getReservations(String tid, String type='modeOff') {
	return parent.getReservations( tid, type )
}
// Get the list of app Names that have reservations
List getGuestList(String tid, String type='modeOff') {
	return parent.getGuestList( tid, type )
}
String textListToString(list) {
	def c = list?.size()
	String s = list.toString()[1..-2]
	if (c == 1) return s.trim()						// statName
	if (c == 2) return s.replace(', ',' and ').trim()	// statName1 and statName2
	int i = s.lastIndexOf(', ')+2
	return (s.take(i) + 'and ' + s.drop(i)).trim()		// statName1, statName2, (...) and statNameN
}
String getMsgPrefix() {
	String thePrefix = ""
	if (settings?.customPrefix == null) { app.updateSetting('customPrefix', '(helper) at (location):'); settings.customPrefix = '(helper) at (location):'; }
	switch (settings?.customPrefix) {
		case '(helper):':
			thePrefix = atomicState.appDisplayName + ': '
			break
		case '(helper) at (location):':
			thePrefix = atomicState.appDisplayName + " at ${location.name}: "
			break
		case '(location):':
			thePrefix = location.name + ': '
			break
		case 'custom':
			thePrefix = settings?.customPrefixText?.trim() + ' '
			break
		case 'none':
			break
	}
	return thePrefix
}
String getMsgContact() {
	String theContact = ""
	if (settings?.contactSensors) {
		if (settings?.customContact == null) {
			app.updateSetting('customContact', 'contact sensors')
			settings?.customContact = 'contact sensors'
			theContact = 'contact sensors'
		} else {
			theContact = (settings?.customContact == 'custom') ? settings?.customContactText : settings?.customContact
		}
	}
	return theContact
}
String getMsgSwitch() {                       
	String theSwitch = ""
	if (settings?.theSwitches) {
		if (settings?.customSwitch == null) {
			app.updateSetting('customSwitch', 'switches')
			settings?.customSwitches = 'switches'
			theSwitch = 'switches'
		} else {
			theSwitch = (settings?.customSwitch == 'custom') ? settings?.customSwitchText : settings?.customSwitch
		}
	}
	return theSwitch
}
String getMsgTstat() {						
	String theTstat = ""
	if (settings?.customTstat == null) { app.updateSetting('customTstat', '(thermostat names) is/are'); settings?.customTstat = '(thermostat names) is/are'; }
	switch (settings.customTstat) {
		case 'custom':
			theTstat = settings.customTstatText 
			break
		case "(thermostat names) is/are":
			def stats = settings?.theThermostats ?: myThermostats
			def nameList = []
			if (settings?.tstatSuffix || settings?.tstatPrefix) {
				stats.each {
					def name = it.displayName
					if (settings.tstatPrefix) name = settings.tstatPrefix + ' ' + name
					if (settings.tstatSuffix) name = name + ' ' + settings.tstatSuffix
					nameList << name
				}
			} else {
				nameList = stats*.displayName
			}
			String statStr = textListToString(nameList)
			if (tstatCleaners != []) {
				tstatCleaners.each{
					if ((!settings?.tstatSuffix || (settings.tstatSuffix != it)) && (!settings?.tstatPrefix || (settings.tstatPrefix != it))) {	// Don't strip the prefix/suffix we added above
						statStr = statStr.replace(it, '').replace(it.toLowerCase(), '')	// Strip out any unnecessary words
					}
				}
			}
			statStr = statStr.replace(':','').replace('  ', ' ').trim()		// Finally, get rid of any double spaces
			theTstat = (statStr + ((stats?.size() > 1) ? ' are' : ' is'))	
			break
		case 'the HVAC system':
			theTstat = 'the H V A C system is'
			break
	}
	return theTstat
}

void sendMessage(notificationMessage) {
	LOG("Notification Message: ${notificationMessage}", 3, null, "info")
    String msg = getMsgPrefix() + notificationMessage
	
	if (atomicState.isST) {
		if (settings.notifiers != null) {
			settings.notifiers.each {									// Use notification devices (if any)
				it.deviceNotification(msg)
			}
		}
		if (phone) { // check that the user did select a phone number
			if ( phone.indexOf(";") > 0){
				def phones = settings.phone.split(";")
				for ( def i = 0; i < phones.size(); i++) {
					LOG("Sending SMS ${i+1} to ${phones[i]}", 3, null, 'info')
					sendSmsMessage(phones[i].trim(), msg)				// Only to SMS contact
				}
			} else {
				LOG("Sending SMS to ${phone}", 3, null, 'info')
				sendSmsMessage(phone.trim(), msg)						// Only to SMS contact
			}
		} 
		if (settings.pushNotify) {
			LOG("Sending Push to everyone", 3, null, 'warn')
			sendPushMessage(msg)										// Push to everyone
		}
		if (settings.speak) {
			if (settings.speechDevices != null) {
				settings.speechDevices.each {
					it.speak( "From " + msg )
				}
			}
			if (settings.musicDevices != null) {
				settings.musicDevices.each {
					it.setLevel( settings.volume )
					it.playText( "From " + msg )
				}
			}
		}
		sendNotificationEvent( notificationMessage )					// Always send to hello home
	} else {		// isHE
		if (settings.notifiers != null) {
			settings.notifiers.each {									// Use notification devices on Hubitat
				it.deviceNotification(msg)
			}
		}
//		if (settings.phone != null) {
//			if ( phone.indexOf(",") > 0){
//				def phones = phone.split(",")
//				for ( def i = 0; i < phones.size(); i++) {
//					LOG("Sending SMS ${i+1} to ${phones[i]}", 3, null, 'info')
//					sendSmsMessage(phones[i].trim(), msg)				// Only to SMS contact
//				}
//			} else {
//				LOG("Sending SMS to ${phone}", 3, null, 'info')
//				sendSmsMessage(phone.trim(), msg)						// Only to SMS contact
//			}
//		}
		if (settings.speak) {
			if (settings.speechDevices != null) {
				settings.speechDevices.each {
					it.speak( "From " + msg )
				}
			}
			if (settings.musicDevices != null) {
				settings.musicDevices.each {
					it.setLevel( settings.volume )
					it.playText( "From " + msg )
				}
			}
		}
		sendLocationEvent(name: "HelloHome", descriptionText: notificationMessage, value: app.label, type: 'APP_NOTIFICATION')
	}
}
void updateMyLabel() {
	String flag = atomicState.isST ? ' (paused)' : '<span '
	
	// Display Ecobee connection status as part of the label...
	String myLabel = atomicState.appDisplayName
	if ((myLabel == null) || !app.label.startsWith(myLabel)) {
		myLabel = app.label
		if (!myLabel.contains(flag)) atomicState.appDisplayName = myLabel
	} 
	if (myLabel.contains(flag)) {
		// strip off any connection status tag
		myLabel = myLabel.substring(0, myLabel.indexOf(flag))
		atomicState.appDisplayName = myLabel
	}
	if (settings.tempDisable) {
		def newLabel = myLabel + (isHE ? '<span style="color:red"> (paused)</span>' : ' (paused)')
		if (app.label != newLabel) app.updateLabel(newLabel)
	} else {
		if (app.label != myLabel) app.updateLabel(myLabel)
	}
}
def pauseOn() {
	// Pause this Helper
	atomicState.wasAlreadyPaused = (settings.tempDisable && !atomicState.globalPause)
	if (!settings.tempDisable) {
		LOG("performing Global Pause",3,null,'info')
		app.updateSetting("tempDisable", true)
		atomicState.globalPause = true
		runIn(2, updated, [overwrite: true])
	} else {
		LOG("was already paused, ignoring Global Pause",3,null,'info')
	}
}
def pauseOff() {
	// Un-pause this Helper
	if (settings.tempDisable) {
		def wasAlreadyPaused = atomicState.wasAlreadyPaused
		if (!wasAlreadyPaused) { // && settings.tempDisable) {
			LOG("performing Global Unpause",3,null,'info')
			app.updateSetting("tempDisable", false)
			runIn(2, updated, [overwrite: true])
		} else {
			LOG("was paused before Global Pause, ignoring Global Unpause",3,null,'info')
		}
	} else {
		LOG("was already unpaused, skipping Global Unpause",3,null,'info')
		atomicState.wasAlreadyPaused = false
	}
	atomicState.globalPause = false
}
void clearReservations() {
	if (settings.theThermostats) {
		theThermostats?.each {
			cancelReservation(getDeviceId(it.deviceNetworkId), 'modeOff')
		}
	}
	if (settings.myThermostats) {
		myThermostats?.each {
    		cancelReservation(getDeviceId(it.deviceNetworkId), 'modeOff')
		}
	}
}
void LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	String msg = "${atomicState.appDisplayName} ${message}"
    if (logType == null) logType = 'debug'
    if ((logType != 'debug') || (!settings.debugOff)) log."${logType}" message
	parent.LOG(msg, level, null, logType, event, displayEvent)
}

// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
String  getPlatform() { return (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
boolean getIsST()     { return (atomicState?.isST != null) ? atomicState.isST : (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
boolean getIsHE()     { return (atomicState?.isHE != null) ? atomicState.isHE : (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...

String getHubPlatform() {
	def pf = getPlatform()
    atomicState?.hubPlatform = pf			// if (atomicState.hubPlatform == 'Hubitat') ... 
											// or if (state.hubPlatform == 'SmartThings')...
    atomicState?.isST = pf.startsWith('S')	// if (atomicState.isST) ...
    atomicState?.isHE = pf.startsWith('H')	// if (atomicState.isHE) ...
    return pf
}
boolean getIsSTHub() { return atomicState.isST }					// if (isSTHub) ...
boolean getIsHEHub() { return atomicState.isHE }					// if (isHEHub) ...

def getParentSetting(String settingName) {
	return isST ? parent?.settings?."${settingName}" : parent?."${settingName}"	
}
