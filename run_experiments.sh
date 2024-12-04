#!/usr/bin/env bash

# HOST refers to the default kernel scheduler
DEFAULT_SCHEDULERS=("HOST" "SampleScheduler")
DEFUALT_REPS=1
DEFUALT_BENCH="finagle-http"

usage()
{
    echo "Run me as root and in the hello-ebpf repo"
	echo "WARNING: I will clear the logs of any previous runs!"
}

run_experiment()
{
	echo "############################"
	echo "# Running experiment...    "
	echo "# With scheduler: $1		 "
	echo "# With reps: $DEFUALT_REPS "
	echo "############################"

	# Run the scheduler
	if [ "$1" != "HOST" ]; then
		./run.sh $1 &
		SCHED_PID=$!
		sleep 1;
		check_if_scheduler_enabled
	fi
	
	# run the benchmark
	time java -jar renaissance-gpl-0.16.0.jar $DEFUALT_BENCH --json $1_bench_results_raw.json -r $DEFUALT_REPS

	
	# grab the average time taken from each repitition
	echo "Average time taken for $DEFUALT_BENCH with $1 scheduler:" >> $1_bench_results.txt
	echo "Nanoseconds:" >> $1_bench_results.txt
	NANOSECS=$(jq '[.data."finagle-http".results[].duration_ns] | add / length' "$1_bench_results_raw.json")
	echo $NANOSECS >> $1_bench_results.txt
	echo "Seconds:" >> $1_bench_results.txt
	echo "${NANOSECS} / 1000000000" | awk '{printf "%.9f\n", $1 / $2}' >> $1_bench_results.txt
	echo >> $1_bench_results.txt

	# grab the average mem usage for each repitition
	echo "Average memory usage for $DEFUALT_BENCH with $1 scheduler:" >> $1_bench_results.txt


	
	# do other stuff
	if [[ $SCHED_PID != "" ]]; then
		echo "Killing scheduler..."
		kill $SCHED_PID
	fi
}

clean_up()
{
	for scheduler in "${DEFAULT_SCHEDULERS[@]}"; do
		rm "${scheduler}_bench_results_raw.json"
		rm "${scheduler}_bench_results.txt"
	done
}

check_if_benchmark_downloaded()
{
	file_to_check="renaissance-gpl-0.16.0.jar"

	script_dir=$(dirname "$(realpath "$0")")

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

	if ! echo "$output" | head -n 1 | grep -q "enabled"; then
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

	clean_up
	check_if_sudo
	check_if_ebpf
	check_if_benchmark_downloaded
	
	for scheduler in "${DEFAULT_SCHEDULERS[@]}"; do
		run_experiment $scheduler
	done
}

main "${@}"

exit 0
