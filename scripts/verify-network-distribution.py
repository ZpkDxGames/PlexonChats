"""Verify independent Java 25 distributions and record evidence; never publish or deploy."""
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile,hashlib,shutil,subprocess,json
version=ET.parse('pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text
out=Path('dist');out.mkdir(exist_ok=True)
totals=dict(tests=0,failures=0,errors=0,skipped=0)
modules={}
for module in ('protocol','paper','velocity'):
    counts=dict.fromkeys(totals,0)
    reports=list(Path(module,'target/surefire-reports').glob('TEST-*.xml'))
    if not reports:raise SystemExit(f'No test reports: {module}')
    for file in reports:
        root=ET.parse(file).getroot()
        for key in totals:counts[key]+=int(root.get(key,0))
    for key in totals:totals[key]+=counts[key]
    modules[module]=counts
if not totals['tests'] or any(totals[k] for k in ('failures','errors','skipped')):raise SystemExit(totals)
checks=[]
for module,name,main,meta in [('paper','PlexonChats','com/antondev/chats/PlexonChats.class','plugin.yml'),('velocity','PlexonChats-Velocity','com/antondev/chats/velocity/PlexonChatsVelocity.class','velocity-plugin.json')]:
    source=Path(module,'target',f'{name}-{version}.jar')
    with zipfile.ZipFile(source) as jar:
        names=jar.namelist()
        for required in (main,meta,'com/antondev/chats/protocol/Protocol.class'):
            assert required in names,(source,required)
        assert int.from_bytes(jar.read(main)[6:8],'big')==69
        for prefix in ('org/bukkit/','io/papermc/paper/','com/velocitypowered/','net/kyori/adventure/','com/zpkdxgames/plexoncore/','github/scarsz/discordsrv/'):
            assert not any(n.startswith(prefix) for n in names),prefix
        if module=='paper':
            assert 'org/sqlite/JDBC.class' in names
            assert 'data.yml' in names
            assert "api-version: '26.3'" in jar.read(meta).decode()
            assert f"version: '{version}'" in jar.read(meta).decode()
        else:assert json.loads(jar.read(meta))['version']==version
    target=out/source.name;shutil.copy2(source,target)
    checks.append(f'{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.name}')
(out/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\n')
(out/'TEST_SUMMARY.json').write_text(json.dumps(dict(total=totals,modules=modules),indent=2)+'\n')
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
(out/'PROVENANCE.txt').write_text(f'repository=ZpkDxGames/PlexonChats\ncommit={sha}\nversion={version}\nbaseline=f98b1a233ca3fc68ac9d94a45dfc414d02634300\njava=25\nclass_major=69\npaper_api=26.3.build.223-stable\nvelocity_api=4.3.0\nlive_runtime_certification=NOT_RUN\n')
print(json.dumps(totals));print('\n'.join(checks))
