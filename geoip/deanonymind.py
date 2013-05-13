#!/usr/bin/env python
import optparse
import os
import sys
import zipfile

"""
Take a MaxMind GeoLite City blocks file as input and replace A1 entries
with the block number of the preceding entry iff the preceding
(subsequent) entry ends (starts) directly before (after) the A1 entry and
both preceding and subsequent entries contain the same block number.

Then apply manual changes, either replacing A1 entries that could not be
replaced automatically or overriding previously made automatic changes.
"""

def main():
    options = parse_options()
    country_blocks = read_location_file(options.in_location)
    assignments = read_file(options.in_maxmind)
    assignments = apply_automatic_changes(assignments,
            options.block_number, country_blocks)
    write_file(options.out_automatic, assignments)
    manual_assignments = read_file(options.in_manual, must_exist=False)
    assignments = apply_manual_changes(assignments, manual_assignments,
            options.block_number)
    write_file(options.out_manual, assignments)

def parse_options():
    parser = optparse.OptionParser()
    parser.add_option('-i', action='store', dest='in_maxmind',
            default='GeoLiteCity-Blocks.csv', metavar='FILE',
            help='use the specified MaxMind GeoLite City blocks .csv '
                 'file as input [default: %default]')
    parser.add_option('-l', action='store', dest='in_location',
            default='GeoLiteCity-Location.csv', metavar='FILE',
            help='use the specified MaxMind GeoLite City location .csv '
                 'file as input [default: %default]')
    parser.add_option('-b', action='store', dest='block_number',
            default=242, metavar='NUM',
            help='replace entries with this block number [default: '
                 '%default]')
    parser.add_option('-g', action='store', dest='in_manual',
            default='geoip-manual', metavar='FILE',
            help='use the specified .csv file for manual changes or to '
                 'override automatic changes [default: %default]')
    parser.add_option('-a', action='store', dest='out_automatic',
            default="Automatic-GeoLiteCity-Blocks.csv", metavar='FILE',
            help='write full input file plus automatic changes to the '
                 'specified .csv file [default: %default]')
    parser.add_option('-m', action='store', dest='out_manual',
            default='Manual-GeoLiteCity-Blocks.csv', metavar='FILE',
            help='write full input file plus automatic and manual '
                 'changes to the specified .csv file [default: %default]')
    (options, args) = parser.parse_args()
    return options

def read_location_file(path):
    if not os.path.exists(path):
        print 'File %s does not exist.  Exiting.' % (path, )
        sys.exit(1)
    countries = {}
    country_blocks = {}
    for line in open(path):
        if line.startswith('C') or line.startswith('l'):
            continue
        keys = ['locId', 'country', 'region', 'city', 'postalCode',
                'latitude', 'longitude', 'metroCode', 'areaCode']
        stripped_line = line.replace('"', '').strip()
        parts = stripped_line.split(',')
        entry = dict((k, v) for k, v in zip(keys, parts))
        if entry['region'] == '':
            countries[entry['country']] = entry['locId']
            country_blocks[entry['locId']] = entry['locId']
        elif entry['country'] in countries:
            country_blocks[entry['locId']] = countries[entry['country']]
    return country_blocks

def read_file(path, must_exist=True):
    if not os.path.exists(path):
        if must_exist:
            print 'File %s does not exist.  Exiting.' % (path, )
            sys.exit(1)
        else:
            return
    csv_file = open(path)
    csv_content = csv_file.read()
    csv_file.close()
    assignments = []
    for line in csv_content.split('\n'):
        stripped_line = line.strip()
        if len(stripped_line) > 0 and not stripped_line.startswith('#'):
            assignments.append(stripped_line)
    return assignments

def apply_automatic_changes(assignments, block_number, country_blocks):
    print '\nApplying automatic changes...'
    result_lines = []
    prev_line = None
    a1_lines = []
    block_number_str = '"%d"' % (block_number, )
    for line in assignments:
        if block_number_str in line:
            a1_lines.append(line)
        else:
            if len(a1_lines) > 0:
                new_a1_lines = process_a1_lines(prev_line, a1_lines, line,
                                                country_blocks)
                for new_a1_line in new_a1_lines:
                    result_lines.append(new_a1_line)
                a1_lines = []
            result_lines.append(line)
            prev_line = line
    if len(a1_lines) > 0:
        new_a1_lines = process_a1_lines(prev_line, a1_lines, None,
                                        country_blocks)
        for new_a1_line in new_a1_lines:
            result_lines.append(new_a1_line)
    return result_lines

def process_a1_lines(prev_line, a1_lines, next_line, country_blocks):
    if not prev_line or not next_line:
        return a1_lines   # Can't merge first or last line in file.
    if len(a1_lines) > 1:
        return a1_lines   # Can't merge more than 1 line at once.
    a1_line = a1_lines[0].strip()
    prev_entry = parse_line(prev_line)
    a1_entry = parse_line(a1_line)
    next_entry = parse_line(next_line)
    touches_prev_entry = int(prev_entry['end_num']) + 1 == \
            int(a1_entry['start_num'])
    touches_next_entry = int(a1_entry['end_num']) + 1 == \
            int(next_entry['start_num'])
    same_block_number = prev_entry['block_number'] == \
            next_entry['block_number']
    same_country = country_blocks[prev_entry['block_number']] == \
            country_blocks[next_entry['block_number']]
    if touches_prev_entry and touches_next_entry:
        if same_block_number:
            new_line = format_line_with_other_country(a1_entry, prev_entry)
            print '-%s\n+%s' % (a1_line, new_line, )
            return [new_line]
        elif same_country:
            new_line = format_line_with_other_country_block(a1_entry,
                    country_blocks[prev_entry['block_number']])
            print '-%s\n+%s' % (a1_line, new_line, )
            return [new_line]
    return a1_lines

def parse_line(line):
    if not line:
        return None
    keys = ['start_num', 'end_num', 'block_number']
    stripped_line = line.replace('"', '').strip()
    parts = stripped_line.split(',')
    entry = dict((k, v) for k, v in zip(keys, parts))
    return entry

def format_line_with_other_country(original_entry, other_entry):
    return '"%s","%s","%s"' % (original_entry['start_num'],
            original_entry['end_num'], other_entry['block_number'], )

def format_line_with_other_country_block(original_entry, country_block):
    return '"%s","%s","%s"' % (original_entry['start_num'],
            original_entry['end_num'], country_block, )

def apply_manual_changes(assignments, manual_assignments, block_number):
    if not manual_assignments:
        return assignments
    print '\nApplying manual changes...'
    block_number_str = '%d' % (block_number, )
    manual_dict = {}
    for line in manual_assignments:
        start_num = parse_line(line)['start_num']
        if start_num in manual_dict:
            print ('Warning: duplicate start number in manual '
                   'assignments:\n  %s\n  %s\nDiscarding first entry.' %
                   (manual_dict[start_num], line, ))
        manual_dict[start_num] = line
    result = []
    for line in assignments:
        entry = parse_line(line)
        start_num = entry['start_num']
        if start_num in manual_dict:
            manual_line = manual_dict[start_num]
            manual_entry = parse_line(manual_line)
            if entry['end_num'] == manual_entry['end_num']:
                if len(manual_entry['block_number']) == 0:
                    print '-%s' % (line, )  # only remove, don't replace
                    del manual_dict[start_num]
                elif entry['block_number'] != manual_entry['block_number']:
                    new_line = format_line_with_other_country(entry,
                            manual_entry)
                    print '-%s\n+%s' % (line, new_line, )
                    result.append(new_line)
                    del manual_dict[start_num]
                else:
                    print ('Warning: automatic and manual replacement '
                           'already match:\n  %s\n  %s\nNot applying '
                           'manual change.' % (line, manual_line, ))
                    result.append(line)
            else:
                print ('Warning: only partial match between '
                       'original/automatically replaced assignment and '
                       'manual assignment:\n  %s\n  %s\nNot applying '
                       'manual change.' % (line, manual_line, ))
                result.append(line)
        elif 'block_number' in entry and \
                entry['block_number'] == block_number_str:
            print ('Warning: no manual replacement for A1 entry:\n  %s'
                % (line, ))
            result.append(line)
        else:
            result.append(line)
    if len(manual_dict) > 0:
        print 'Warning: could not apply all manual assignments:'
        for line in manual_dict.values():
            print '  %s' % (line, )
    return result

def write_file(path, assignments):
    out_file = open(path, 'w')
    out_file.write('\n'.join(assignments))
    out_file.close()

if __name__ == '__main__':
    main()

