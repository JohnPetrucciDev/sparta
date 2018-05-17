'use strict'

Vue.use(Vuex)

Vue.component('error', {
  template: `
    <section id="step1" class="wrapper section">
        <div class="centered">
            <div class="c-content">
                <h1>NODE SPA</h1>
                <template v-if="$store.getters.isDown">
                    <p class="question">
                        Node is down
                    </p>
                </template>
                <template v-else>
                  <p v-for="(message, key) in $store.getters.errors" class="question" style="color:red">
                      {{key}}: {{message}}
                  </p>
                </template>
            </div>
        </div>
    </section>
  `
})

Vue.component('downloading', {
  template: `
    <section id="step1" class="wrapper section">
        <div class="centered">
            <div class="c-content">
                <h1>NODE SPA</h1>
                <p class="question">
                    Blockchain is downloading now, please wait.<br>
                    Progress: {{$store.getters.downloadingProgress}}%
                </p>
                <form>
                    <input
                        type="submit"
                        class="black"
                        value="Shutdown Node"
                        v-on:click.prevent="$store.dispatch('down')"
                    >
                </form>
            </div>
        </div>
    </section>
  `
})

Vue.component('password', {
  template: `
    <section id="step1" class="wrapper section">
        <div class="centered">
            <div class="c-content">
                <h1>NODE SPA</h1>
                <p class="question">
                    Needs admin password
                </p>
                <form>
                    <textarea
                        v-model="adminPassword"
                        name="adminPassword"
                        id="adminPassword"
                        class="incorrect"></textarea>
                    <input
                        type="submit"
                        class="black"
                        value="Log In"
                        v-on:click.prevent="$store.dispatch('logIn')"
                        :disabled="!$store.getters.hasAdminPassword"
                    >
                </form>
            </div>
        </div>
    </section>
  `,
  computed: {
    adminPassword: {
      get () {
        return this.$store.getters.adminPassword
      },
      set (adminPassword) {
        this.$store.commit('adminPassword', adminPassword)
      }
    }
  }
})

Vue.component('start-forging', {
  template: `
    <section id="step1" class="wrapper section">
        <div class="centered">
            <div class="c-content">
                <h1>NODE SPA</h1>
                <p class="question">
                    To start forging enter you mnemonic code: <!--span class="help-icon"><span class="help">Mnemonic code is...</span></span-->
                </p>
                <form>
                    <textarea
                        v-model="secretPhrase"
                        name="code"
                        id="code"
                        class="incorrect"></textarea>
                    <input
                        type="submit"
                        class="black"
                        value="Start Forging"
                        v-on:click.prevent="$store.dispatch('startForging')"
                        :disabled="!$store.getters.hasSecretPhrase"
                    >
                </form>
                <form>
                    <input
                        type="submit"
                        class="black"
                        value="Shutdown Node"
                        v-on:click.prevent="$store.dispatch('down')"
                    >
                </form>
            </div>
        </div>
    </section>
  `,
  computed: {
    secretPhrase: {
      get () {
        return this.$store.getters.secretPhrase
      },
      set (secretPhrase) {
        this.$store.commit('secretPhrase', secretPhrase)
      }
    }
  }
})

Vue.component('stop-forging', {
  template: `
    <section id="step2" class="wrapper section">
        <div class="centered">
            <div class="c-content">
                <h1>NODE SPA</h1>
                <p>Forging is in progress</p>
                <form>
                    <div class="loading light1">
                        processing...
                    </div>
                    <input
                        type="submit"
                        class="red"
                        value="Stop Forging"
                        v-on:click.prevent="$store.dispatch('stopForging')"
                    >
                </form>
                <p class="after">Your estimated time to forge a new block is {{ $store.getters.remaining }} seconds</p>
            </div>
        </div>
    </section>
  `
})

const store = new Vuex.Store({
  state: {
    secretPhrase: '',
    adminPassword: '',
    loggedIn: false,
    needsAdminPassword: true,
    isDownloading: true,
    isDown: false,
    lastBlockchainFeederHeight: 1,
    numberOfBlocks: 1,
    generators: [],
    errors: {}
  },
  getters: {
    isDownloading: state => state.isDownloading,
    downloadingProgress: getters => {
      let perc = 0
      if (getters.lastBlockchainFeederHeight > 0) {
        perc = (100 / getters.lastBlockchainFeederHeight * getters.numberOfBlocks)
      }
      if (perc == Infinity) {
        perc = 0
      }
      return perc.toFixed(2)
    },
    lastBlockchainFeederHeight: state => state.lastBlockchainFeederHeight,
    numberOfBlocks: state => state.numberOfBlocks,
    secretPhrase: state => state.secretPhrase,
    hasSecretPhrase: state => state.secretPhrase.length > 0,
    needsAdminPassword: state => state.needsAdminPassword,
    generators: state => state.generators,
    hasGenerators: state => state.generators.length > 0,
    remaining: state => state.generators[0].remaining,
    errors: state => state.errors,
    hasErrors: state => Object.keys(state.errors).length > 0,
    adminPassword: state => state.adminPassword,
    hasAdminPassword: state => state.adminPassword.length > 0,
    loggedIn: state => !state.isDown && (!state.needsAdminPassword || state.loggedIn),
    isDown: state => state.isDown
  },
  mutations: {
    isDown (state, payload) {
      state.isDown = payload
    },
    loggedIn (state, payload) {
      state.loggedIn = payload
    },
    numberOfBlocks (state, payload) {
      state.numberOfBlocks = payload
    },
    lastBlockchainFeederHeight (state, payload) {
      state.lastBlockchainFeederHeight = payload
    },
    secretPhrase (state, payload) {
      state.secretPhrase = payload
    },
    adminPassword (state, payload) {
      state.adminPassword = payload
    },
    needsAdminPassword (state, payload) {
      state.needsAdminPassword = payload
    },
    isDownloading (state, payload) {
      state.isDownloading = payload
    },
    generators (state, payload) {
      state.generators = payload
    },
    error (state, payload) {
      Vue.set(state.errors, payload.key, payload.message)
    },
    noError (state, payload) {
      Vue.delete(state.errors, payload.key)
    }
  },
  actions: {
    init ({dispatch}) {
      dispatch('getState')
      setInterval(() => {dispatch('getState')}, 1000)
      setInterval(() => {dispatch('getForging')}, 1000)
    },
    logIn ({getters, commit}) {
      if (getters.needsAdminPassword && getters.hasAdminPassword) {
        const form = new FormData()
        form.append('adminPassword', getters.adminPassword)
        fetch('/spa?requestType=getForging',
          {
            method: 'POST',
            body: form
          })
          .then(res => res.json())
          .then(json => {
            if (typeof json.errorDescription !== 'undefined') {
              throw new Error(json.errorDescription)
            }
            commit('loggedIn', true)
          })
          .catch(alert)
      }
    },
    getState ({commit, dispatch}) {
      fetch('/spa?requestType=getState')
        .then(res => res.json())
        .then(json => {
          if (typeof json.errorDescription !== 'undefined') {
            throw new Error(json.errorDescription)
          }
          commit('noError', {key: 'getState'})
          commit('needsAdminPassword', json.needsAdminPassword)
          commit('isDownloading', (json.isDownloading || json.blockchainState == 'DOWNLOADING'))
          commit('numberOfBlocks', json.numberOfBlocks)
          commit('lastBlockchainFeederHeight', json.lastBlockchainFeederHeight)
          if (!json.needsAdminPassword) {
            dispatch('getForging')
          }
          commit('isDown', false)
        })
        .catch(err => {
          commit('error', {key: 'getState', message: err.message})
        })
    },
    getForging ({getters, commit}) {
      if (getters.loggedIn) {
        const form = new FormData()
        if (getters.needsAdminPassword) {
          form.append('adminPassword', getters.adminPassword)
        }
        fetch('/spa?requestType=getForging',
          {
            method: 'POST',
            body: form
          })
          .then(res => res.json())
          .then(json => {
            if (typeof json.errorDescription !== 'undefined') {
              throw new Error(json.errorDescription)
            }
            commit('noError', {key: 'getForging'})
            commit('generators', json.generators)
          })
          .catch(err => {
            commit('error', {key: 'getForging', message: err.message})
          })
      }
    },
    startForging ({getters, commit}) {
      if (getters.loggedIn) {
        const form = new FormData()
        form.append('secretPhrase', getters.secretPhrase)
        if (getters.needsAdminPassword) {
          form.append('adminPassword', getters.adminPassword)
        }
        fetch('/spa?requestType=startForging',
          {
            method: 'POST',
            body: form
          })
          .then(res => res.json())
          .then(json => {
            if (typeof json.errorDescription !== 'undefined') {
              throw new Error(json.errorDescription)
            }
            commit('secretPhrase', '')
          })
          .catch(alert)
      }
    },
    stopForging ({getters}) {
      if (getters.loggedIn) {
        const form = new FormData()
        if (getters.needsAdminPassword) {
          form.append('adminPassword', getters.adminPassword)
        }
        fetch('/spa?requestType=stopForging',
          {
            method: 'POST',
            body: form
          })
          .then(res => res.json())
          .then(json => {
            if (typeof json.errorDescription !== 'undefined') {
              throw new Error(json.errorDescription)
            }
          })
          .catch(alert)
      }
    },
    down ({getters, commit}) {
      if (getters.loggedIn) {
        const form = new FormData()
        if (getters.needsAdminPassword) {
          form.append('adminPassword', getters.adminPassword)
        }
        fetch('/spa?requestType=shutdown',
          {
            method: 'POST',
            body: form
          })
          .then(res => res.json())
          .then(json => {
            if (typeof json.errorDescription !== 'undefined') {
              throw new Error(json.errorDescription)
            }
            commit('isDown', true)
          })
          .catch(alert)
      }
    }
  }
})

const app = new Vue({
  store,
  el: '#app',
  template: `
    <error v-if="$store.getters.hasErrors"/>
    <password v-else-if="!$store.getters.loggedIn"/>
    <downloading v-else-if="$store.getters.isDownloading"/>
    <stop-forging v-else-if="$store.getters.hasGenerators"/>
    <start-forging v-else/>
  `
})

store.dispatch('init')
