# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/) and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


<!-- Template:
## [Unreleased] - 2019-xx-xx

Optional intro comment.

- feat: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- fix: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- ui: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- perf: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- remove: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- docs: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)
- build: Abc ([#](https://github.com/JonasWanke/Unicorn/pull/)), fixes [#](https://github.com/JonasWanke/Unicorn/issues/)

 -->

## [Unreleased] - see [milestones] for our roadmap


<a name="0.1.0"></a>
## [0.1.0](https://github.com/JonasWanke/Unicorn/compare/v0.0.2...v0.1.0) - 2020-01-01

This release extends Unicorn's functionality to support custom scripting, templating and running as a GitHub Action.

### Features

- **commands:** add component, priority and type commands ([#66](https://github.com/JonasWanke/Unicorn/pull/66))
- **commands:** push files on issue complete ([#71](https://github.com/JonasWanke/Unicorn/pull/71)), fixes [#39](https://github.com/JonasWanke/Unicorn/issues/39)
- **commands:** support assigning multiple users to an issue ([#71](https://github.com/JonasWanke/Unicorn/pull/71)), fixes [#46](https://github.com/JonasWanke/Unicorn/issues/46)
- **script, action:** support scripting, GitHub action ([#50](https://github.com/JonasWanke/Unicorn/pull/50)), fixes [#33](https://github.com/JonasWanke/Unicorn/issues/33), [#43](https://github.com/JonasWanke/Unicorn/issues/43), [#45](https://github.com/JonasWanke/Unicorn/issues/45)
- **template, commands:** support custom file templates ([#56](https://github.com/JonasWanke/Unicorn/pull/56)), fixes [#49](https://github.com/JonasWanke/Unicorn/issues/49), [#55](https://github.com/JonasWanke/Unicorn/issues/55)
- **template, templates:** move to pure scripting; add dart templates ([#62](https://github.com/JonasWanke/Unicorn/pull/62)), fixes [#59](https://github.com/JonasWanke/Unicorn/issues/59)
- **templates:** improve dart templates ([#65](https://github.com/JonasWanke/Unicorn/pull/65)), fixes [#63](https://github.com/JonasWanke/Unicorn/issues/63)
- **templates:** add kotlin template ([#64](https://github.com/JonasWanke/Unicorn/pull/64)), fixes [#60](https://github.com/JonasWanke/Unicorn/issues/60)

### Bug Fixes

- **commands:** create correct labels using templates ([#73](https://github.com/JonasWanke/Unicorn/pull/73)), fixes [#58](https://github.com/JonasWanke/Unicorn/issues/58)

### Docs

- simplify GitHub templates ([#54](https://github.com/JonasWanke/Unicorn/pull/54)), fixes [#52](https://github.com/JonasWanke/Unicorn/issues/52)

### Refactor

- **core:** clearer structure for categorizations ([#70](https://github.com/JonasWanke/Unicorn/pull/70))

### Build

- update libraries ([#53](https://github.com/JonasWanke/Unicorn/pull/53)), fixes [#51](https://github.com/JonasWanke/Unicorn/issues/51)

### CI

- use GitHub Actions ([#74](https://github.com/JonasWanke/Unicorn/pull/74)), fixes [#68](https://github.com/JonasWanke/Unicorn/issues/68)

### Chore

- change action name ([#75](https://github.com/JonasWanke/Unicorn/pull/75))



<a name="0.0.2"></a>
## [0.0.2](https://github.com/JonasWanke/Unicorn/compare/v0.0.1...v0.0.2) - 2019-04-09

### Features

- use unicorn ([#37](https://github.com/JonasWanke/Unicorn/pull/37)), fixes [#34](https://github.com/JonasWanke/Unicorn/issues/34)
- **commands/init:** support init in existing repo ([#36](https://github.com/JonasWanke/Unicorn/pull/36)), fixes [#28](https://github.com/JonasWanke/Unicorn/issues/28), [#20](https://github.com/JonasWanke/Unicorn/issues/20), [#27](https://github.com/JonasWanke/Unicorn/issues/27)
- **commands/login:** remove password support ([#42](https://github.com/JonasWanke/Unicorn/pull/42)), fixes [#32](https://github.com/JonasWanke/Unicorn/issues/32)


### Bug Fixes

- **commands/init:** only create .gitignore if templates were selected ([#41](https://github.com/JonasWanke/Unicorn/pull/41)), fixes [#30](https://github.com/JonasWanke/Unicorn/issues/30)


### Docs

- **commands/init:** fix/shorten templates and labels ([#44](https://github.com/JonasWanke/Unicorn/pull/44)), fixes [#35](https://github.com/JonasWanke/Unicorn/issues/35)


### Refactor

- rename config files ([#40](https://github.com/JonasWanke/Unicorn/pull/40)), fixes [#29](https://github.com/JonasWanke/Unicorn/issues/29)



## 0.0.1 - 2019-01-30

Initial release supporting repository initialization.


[milestones]: https://github.com/JonasWanke/Unicorn/milestones
[Unreleased]: https://github.com/JonasWanke/Unicorn/compare/v0.1.0...dev
