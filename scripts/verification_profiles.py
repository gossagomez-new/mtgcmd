"""Fixed local scopes reusing the repository's existing verification commands."""

from dataclasses import dataclass


PROFILES = ('frontend', 'backend', 'full', 'browser')


@dataclass(frozen=True)
class Check:
    id: str
    argv: tuple
    profiles: tuple


CHECKS = (
    Check('frontend-lint', ('npm', '--prefix', 'frontend', 'run', 'lint'), ('frontend', 'full')),
    Check('frontend-format', ('npm', '--prefix', 'frontend', 'run', 'format:check'), ('frontend', 'full')),
    Check('frontend-unit', ('npm', '--prefix', 'frontend', 'test'), ('frontend', 'full')),
    Check('analyzer-tests', ('npm', '--prefix', 'frontend', 'run', 'test:analysis'), ('frontend', 'full')),
    Check('frontend-analysis', ('npm', '--prefix', 'frontend', 'run', 'analyze'), ('frontend',)),
    Check('frontend-build', ('npm', '--prefix', 'frontend', 'run', 'build'), ('frontend',)),
    Check('wrapper-tests', ('node', '--test', '--test-isolation=none',
                            'scripts/build.test.mjs', 'scripts/pmd.test.mjs',
                            'scripts/jacoco.test.mjs', 'scripts/pit.test.mjs'), ('full',)),
    Check('python-tests', ('python3', '-B', '-m', 'unittest', 'discover', '-s',
                           'scripts', '-p', 'test_*.py'), ('full',)),
    Check('specifications', ('python3', '-B', 'scripts/validate-specs.py'), ('full',)),
    Check('backend-verify', ('mvn', '-f', 'backend/pom.xml', 'spotless:check', 'verify'), ('backend',)),
    Check('backend-format', ('mvn', '-f', 'backend/pom.xml', 'spotless:check'), ('full',)),
    Check('package', ('bash', 'scripts/build.sh'), ('full',)),
    Check('browser', ('npm', '--prefix', 'frontend', 'run', 'test:e2e'), ('browser',)),
)


def plan(profile, browser):
    return [
        {'id': check.id, 'argv': list(check.argv),
         'selected': profile in check.profiles or (check.id == 'browser' and browser)}
        for check in CHECKS
    ]
