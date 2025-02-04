#!/usr/bin/env bash

# HOST refers to the default kernel scheduler
DEFAULT_SCHEDULERS=("HOST" "FIFOScheduler" "WeightedScheduler" "LotteryScheduler")
#DEFAULT_SCHEDULERS=("FooScheduler" "FIFOScheduler")
DEFUALT_REPS=100
# DEFUALT_BENCH="finagle-http"
# DEFUALT_BENCH="finagle-chirper"
BENCHMARK_JAR="renaissance-gpl-0.16.0-7-gd3bb48c.jar"

set -o pipefail

usage()
{
    echo "Run me as root and in the hello-ebpf repo"
	echo "WARNING: I will clear the logs of any previous runs!"
}

run_experiment2()
{
	local sched="$1"

	# Run the scheduler
	if [ "$sched" != "HOST" ]; then
		./run.sh "$sched" &
		SCHED_PID=$!
		sleep 5;
		check_if_scheduler_enabled
	fi

	# Execute x times


	for (( i=0; i<DEFUALT_REPS; i++ ))
	do
		echo "$i"
		local outdir=""
		
		outdir="output/http/$sched/$i"
		mkdir -p "$outdir"
		/usr/bin/time -o "$outdir/time.txt" \
			-f "real_time: %e \nkernel_cpu: %S \nuser_cpu: %U \ncpu_percentage: %P \nexpired: %c \nwaits: %w " \
			java -jar $BENCHMARK_JAR "finagle-http" \
			--json "$outdir/results.json" -r 1

		outdir="output/chirper/$sched/$i"
		mkdir -p "$outdir"
		/usr/bin/time -o "$outdir/time.txt" \
			-f "real_time: %e \nkernel_cpu: %S \nuser_cpu: %U \ncpu_percentage: %P \nexpired: %c \nwaits: %w " \
			java -jar $BENCHMARK_JAR "finagle-chirper" \
			--json "$outdir/results.json" -r 1
		
		outdir="output/ipctcp/$sched/$i"
		mkdir -p "$outdir"
		/usr/bin/time -o "$outdir/time.txt" \
			-f "real_time: %e \nkernel_cpu: %S \nuser_cpu: %U \ncpu_percentage: %P \nexpired: %c \nwaits: %w " \
			./ipc-bench/build/source/tcp/tcp -c 100000
		
		outdir="output/ipcmq/$sched/$i"
		mkdir -p "$outdir"
		/usr/bin/time -o "$outdir/time.txt" \
			-f "real_time: %e \nkernel_cpu: %S \nuser_cpu: %U \ncpu_percentage: %P \nexpired: %c \nwaits: %w " \
			./ipc-bench/build/source/mq/mq -c 100000

	done


	if [[ $SCHED_PID != "" ]]; then
		echo "Killing scheduler... $SCHED_PID"
		pkill -P $SCHED_PID
	fi
}



run_experiment()
{
	echo "############################"
	echo "# Running experiment...    "
	echo "# With scheduler: $1		 "
	echo "# For $DEFUALT_REPS repititions"
	echo "############################"

	# Run the scheduler
	if [ "$1" != "HOST" ]; then
		./run.sh $1 &
		SCHED_PID=$!
		sleep 5;
		check_if_scheduler_enabled
	fi
	
	echo "" >> $1_bench_results.txt
	echo "############################" >> $1_bench_results.txt
	echo "" >> $1_bench_results.txt

	echo "CPU Usage for scheduler $1:" >> $1_bench_results.txt
	/usr/bin/time -f "    CPU USAGE: %P" java -jar $BENCHMARK_JAR $DEFUALT_BENCH --json $1_bench_results_raw.json -r $DEFUALT_REPS > $1_bench_jar_output.txt 2>> $1_bench_results.txt
	echo >> $1_bench_results.txt
	
	# grab the average time taken from each repitition
	echo "Average time taken for $DEFUALT_BENCH with $1 scheduler:" >> $1_bench_results.txt
	echo "    Nanoseconds:" >> $1_bench_results.txt
	NANOSECS=$(jq '[.data."finagle-http".results[].duration_ns] | add / length' "$1_bench_results_raw.json")
	echo "    $NANOSECS" >> $1_bench_results.txt
	echo "    Seconds:" >> $1_bench_results.txt
	echo "${NANOSECS}  1000000000" | awk '{printf "    %.9f\n", $1 / $2}' >> $1_bench_results.txt
	echo >> $1_bench_results.txt

	# grab the mem usage for the whole run
	HEAP_USAGE=$(jq '[.environment.vm.memory.heap_usage.used] | add / length' "$1_bench_results_raw.json")
	NONHEAP_USAGE=$(jq '[.environment.vm.memory.nonheap_usage.used] | add / length' "$1_bench_results_raw.json")

	echo "Average memory usage for $DEFUALT_BENCH with $1 scheduler:" >> "$1_bench_results.txt"
	echo "    Heap Used (Bytes):" >> "$1_bench_results.txt"
	echo "    $HEAP_USAGE" >> "$1_bench_results.txt"

	echo "    Heap Used (MB):" >> "$1_bench_results.txt"
	echo "$HEAP_USAGE  1048576" | awk '{printf "    %.3f\n", $1 / $2}' >> "$1_bench_results.txt"

	echo "    Non-Heap Used (Bytes):" >> "$1_bench_results.txt"
	echo "    $NONHEAP_USAGE" >> "$1_bench_results.txt"

	echo "    Non-Heap Used (MB):" >> "$1_bench_results.txt"
	echo "$NONHEAP_USAGE  1048576" | awk '{printf "    %.3f\n", $1 / $2}' >> "$1_bench_results.txt"

	echo >> "$1_bench_results.txt"

	echo "Server cold start time (ms):" >> "$1_bench_results.txt"
	server_cold_start=$(grep "Server cold start latency" "$1_bench_jar_output.txt" | awk '{print $5}')
	echo "    $server_cold_start" >> "$1_bench_results.txt"

	echo >> "$1_bench_results.txt"


	client_latencies=$(grep "Average client cold start latency" "$1_bench_jar_output.txt" | awk '{print $6}')

	sum=0
	for latency in $client_latencies; do
		sum=$(echo "$sum + $latency" | bc -l)
	done
	
	average=$(echo "$sum / $DEFUALT_REPS" | bc -l)

	echo "Average client cold start time (ms):" >> "$1_bench_results.txt"
	echo "     $average" >> "$1_bench_results.txt"
	
	echo >> "$1_bench_results.txt"

	if [[ $SCHED_PID != "" ]]; then
		echo "Killing scheduler... $SCHED_PID"
		pkill -P $SCHED_PID
	fi
}

clean_up()
{
	rm *_bench_results_raw.json
	rm *_bench_results.txt
	rm *_bench_jar_output.txt

	rm compiled_results.txt
}

check_if_benchmark_downloaded()
{
	script_dir=$(dirname "$(realpath "$0")")

	if [ ! -f "$script_dir/$BENCHMARK_JAR" ]; then
		echo "The benchmarker '$BENCHMARK_JAR' does not exist in the same directory as the script!"
		echo "Downloading..."
		wget https://github.com/donalshortt/VU-DS-hello-ebpf/releases/download/renaissance/renaissance-gpl-0.16.0-7-gd3bb48c.jar 
	fi

	if [ ! -f "$script_dir/ipc-bench/README.md" ]; then
		echo "ipc-bench missing"
		echo "Downloading..."
		wget https://github.com/donalshortt/VU-DS-hello-ebpf/releases/download/renaissance/ipc-bench.tar
		tar -xvf ipc-bench.tar 
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
		echo "Have you run build.sh?"
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
	check_if_benchmark_downloaded

	rm -rf ./output
	mkdir ./output

	for scheduler in "${DEFAULT_SCHEDULERS[@]}"; do
		run_experiment2 $scheduler
	done

	sudo chown user ./output

	tar -cvf output.tar output

	# # Compile results
	# for scheduler in "${DEFAULT_SCHEDULERS[@]}"; do
	# 	cat "${scheduler}_bench_results.txt" >> compiled_results.txt
	# done

	# echo "Experiment complete!"
	# echo "Raw data saved in *_bench_results_raw.json"
	# echo "Processed data saved in *_bench_results.txt"
	# echo "Results compiled in compiled_results.txt"
}

main "${@}"

exit 0
