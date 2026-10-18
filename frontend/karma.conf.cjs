const path = require('node:path');

module.exports = (config) => {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
    ],
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--headless', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    coverageReporter: {
      dir: path.join(__dirname, 'coverage/dispute-portal'),
      subdir: '.',
      reporters: [
        {type: 'html'},
        {type: 'lcovonly'},
        {type: 'text-summary'},
      ],
      check: {
        global: {
          statements: 50,
          branches: 40,
          functions: 50,
          lines: 50,
        },
      },
    },
    restartOnFileChange: true,
  });
};
