#!/bin/bash

# HOST refers to the default kernel scheduler
default_schedulers=("HOST" "SampleScheduler")

usage()
{
    echo "Run me as root and in the hello-ebpf repo"
}

run_experiment()
{
	echo "Running experiment... "
	echo "With scheduler: $1"

	# Run the scheduler
	if [ "$1" != "HOST" ]; then
		./run.sh $1
		sleep 1;
		check_if_scheduler_enabled
	fi
	
	# Run the benchmark
	time java -jar renaissance-gpl-0.16.0.jar finagle-http --json $1_bench_results.txt.2 -r 1
	grep -E "Elapsed|Maximum resident set size|Percent of CPU" $1_bench_results.txt
	
	

	# change the compiler
	# collect the et, mem & cpu
	#
	

}

check_if_benchmark_downloaded()
{
	file_to_check="renaissance-gpl-0.16.0.jar"

	script_dir=$(dirname "$(realpath "$0")")

	echo "WEEEE:$script_dir/$file_to_check "

	if [ ! -f "$script_dir/$file_to_check" ]; then
		echo "The benchmarker '$file_to_check' does NOT exist in the same directory as the script. Downloading..."
		wget https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.16.0/renaissance-gpl-0.16.0.jar
	fi
}

check_if_sudo()
{
	if [ "$EUID" -ne 0 ]; then
		echo "ERROR: This script must be run as root!"
		exit 1
	fi
}

check_if_ebpf()
{
	# Define the expected directory name
	expected_dirname="VU-DS-hello-ebpf"

	# Get the directory where the script is located
	actual_dirname=$(basename "$(dirname "$(realpath "$0")")")

	# Compare the directory names
	if [ "$actual_dirname" != "$expected_dirname" ]; then
		echo "ERROR: This script is not being run from the correct directory. It's in: $actual_dirname"
		echo "We want to be in: $expected_dirname"
		exit 1
	fi
}

check_if_scheduler_enabled()
{
	output=$(cat /sys/kernel/sched_ext/state /sys/kernel/sched_ext/*/ops 2>/dev/null)

	if echo "$output" | head -n 1 | grep -q "enabled"; then
		echo "Custom scheduler enabled!"
	else
		echo "ERROR: Enabling custom scheduler failed!"
		exit 1
	fi
}

main()
{
    while getopts ":h" options; do
        case "${options}" in
        h)
            usage
            exit 0
            ;;
        esac
    done
    shift "$((OPTIND - 1))"

	check_if_sudo
	check_if_ebpf
	check_if_benchmark_downloaded
	
	for scheduler in "${default_schedulers[@]}"; do
		run_experiment $scheduler
	done
}

main "${@}"

exit 0
