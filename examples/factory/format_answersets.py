#!/usr/bin/env python3

import json, sys, logging, collections, re

def msg(what):
  sys.stdout.write(what+'\n')

def formatjson(j):
  if isinstance(j, dict):
    assert('name' in j and 'args' in j)
    assert(len(j['args']) > 0)
    return "{}({})".format(j['name'], ','.join([ formatjson(x) for x in j['args'] ]))
  elif isinstance(j, list):
    return "[{}]".format(' , '.join([ formatjson(x) for x in j ]))
  else:
    s = str(j)
    # simplify URIs
    s = re.sub(r'^"http:\/\/[^#]+', '"...', s)
    return s

def main():
  for line in sys.stdin:
    l = line.strip()
    if l == '':
      continue
    answer = json.loads(l)
    msg("= Answer Set with cost "+str([ '{}@{}'.format(c['cost'], c['priority']) for c in answer['cost'] ]))

    # display raw
    #for a in answer['stratoms']:
    #  msg("  [raw] "+a)

    bytime = collections.defaultdict(lambda: { 'actions': [], 'states': [] })
    general = { 'explanations': [] }
    for a in answer['atoms']:
      logging.debug("processing atom %s", a)
      handled = False
      if isinstance(a, dict):
        if a['name'] in ['do']:
          assert(len(a['args']) == 2)
          spec, t = a['args']
          assert(isinstance(t, int))
          bytime[t]['actions'].append( spec )
          handled = True
        elif a['name'] in ['state']:
          assert(len(a['args']) == 3)
          what, prop, t = a['args']
          assert(isinstance(t, int))
          bytime[t]['states'].append( (what, prop) )
          handled = True
        elif a['name'] in ['explanation']:
          assert(len(a['args']) == 1)
          what = a['args'][0]
          general['explanations'].append( what )
          handled = True
      if not handled:
        logging.warning(" unhandled atom: %s", a)
    
    times = range(0, max(bytime.keys())+1)
    for t in times:
      atomcnt = len(bytime[t]['actions']) + len(bytime[t]['states'])
      msg("Time Step {}: {} atoms".format(t, atomcnt))
      for what, prop in sorted(bytime[t]['states'], key=lambda s:s[0]):
        msg("  {} is {}".format(formatjson(what), formatjson(prop)))
      for a in sorted(bytime[t]['actions'], key=lambda a:a['name']+'.'+str(a['args'][0])):
        msg("  action "+formatjson(a))
    if len(general['explanations']) > 0:
      msg("Explanations")
      for a in sorted(general['explanations'], key=lambda a:str(a)):
        msg("  explanation "+formatjson(a))

main()
