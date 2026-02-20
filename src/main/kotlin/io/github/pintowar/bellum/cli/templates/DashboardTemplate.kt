package io.github.pintowar.bellum.cli.templates

object DashboardTemplate {
    fun generateHtml(jsonData: String): String =
        """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bellum Interactive Dashboard</title>
    <!-- React & ReactDOM -->
    <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
    <!-- Babel standalone for JSX -->
    <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
    <!-- Apache ECharts -->
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #262626; color: #ddd; margin: 0; padding: 20px; }
        .dashboard { max-width: 1200px; margin: 0 auto; display: grid; gap: 20px; }
        .card { background: #333; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.5); }
        .row { display: flex; gap: 20px; flex-wrap: wrap; }
        .col { flex: 1; min-width: 300px; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { border: 1px solid #555; padding: 8px; text-align: left; }
        th { background-color: #444; }
        input[type=range] { width: 100%; }
        h2 { margin-top: 0; font-size: 1.2rem; color: #fff; }
    </style>
</head>
<body>
    <div id="root"></div>

    <script>
        window.BELLUM_DATA = $jsonData;
    </script>
    
    <script type="text/babel">
        const { useState, useEffect, useRef } = React;

        const parseDurationMatch = (ptDuration) => {
            // Very simple PT duration parser (e.g., PT30M, PT2H, PT1H30M)
            let mins = 0;
            const hoursMatch = ptDuration.match(/(\d+)H/);
            const minsMatch = ptDuration.match(/(\d+)M/);
            if (hoursMatch) mins += parseInt(hoursMatch[1], 10) * 60;
            if (minsMatch) mins += parseInt(minsMatch[1], 10);
            return mins;
        }

        const parseDuration = (dur) => {
            if (!dur) return 0;
            if (typeof dur === 'string' && dur.startsWith('PT')) {
                return parseDurationMatch(dur);
            }
            return 0; // fallback if numbers or different formats
        }

        const GanttChart = ({ solution, project }) => {
            const chartRef = useRef(null);

            useEffect(() => {
                if (!chartRef.current || !solution || !project) return;
                const myChart = echarts.init(chartRef.current, 'dark');
                myChart.setOption({ backgroundColor: 'transparent' }); // align with our theme
                
                const employees = project.employees.map(e => e.name);
                
                const dataPairs = [];
                const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'];

                project.tasks.forEach((t, i) => {
                    if (t.employee && t.startAt) {
                        const start = new Date(t.startAt).getTime();
                        const dur = parseDuration(t.duration) * 60000;
                        const end = start + dur;
                        const empIndex = employees.indexOf(t.employee.name);
                        
                        // Pick a color based on task name to make it look nicer
                        const color = colors[i % colors.length];

                        dataPairs.push({
                            name: t.name,
                            value: [
                                empIndex,
                                start,
                                end,
                                parseDuration(t.duration)
                            ],
                            itemStyle: { normal: { color: color } }
                        });
                    }
                });

                function renderItem(params, api) {
                    const categoryIndex = api.value(0);
                    const start = api.coord([api.value(1), categoryIndex]);
                    const end = api.coord([api.value(2), categoryIndex]);
                    const height = api.size([0, 1])[1] * 0.6;

                    // Ensure minimum width to be visible
                    let width = end[0] - start[0];
                    if (width < 2) width = 2; 

                    const rectShape = echarts.graphic.clipRectByRect({
                        x: start[0],
                        y: start[1] - height / 2,
                        width: width,
                        height: height
                    }, {
                        x: params.coordSys.x,
                        y: params.coordSys.y,
                        width: params.coordSys.width,
                        height: params.coordSys.height
                    });

                    return rectShape && {
                        type: 'rect',
                        transition: ['shape'],
                        shape: rectShape,
                        style: api.style()
                    };
                }

                const option = {
                    tooltip: {
                        formatter: function (params) {
                            return params.marker + params.name + ': ' + params.value[3] + ' min';
                        }
                    },
                    dataZoom: [
                        { type: 'slider', filterMode: 'weakFilter', showDataShadow: false, bottom: 0, height: 20 }, 
                        { type: 'inside', filterMode: 'weakFilter' }
                    ],
                    grid: { height: 300, top: 40, bottom: 40 },
                    xAxis: {
                        type: 'time',
                        scale: true,
                        axisLabel: {
                            formatter: function (val) {
                                // show relative time or formatted date
                                const d = new Date(val);
                                return echarts.format.formatTime('hh:mm', d);
                            }
                        }
                    },
                    yAxis: {
                        data: employees
                    },
                    series: [{
                        type: 'custom',
                        renderItem: renderItem,
                        encode: {
                            x: [1, 2],
                            y: 0
                        },
                        data: dataPairs
                    }]
                };

                myChart.setOption(option, true);

                const handleResize = () => myChart.resize();
                window.addEventListener('resize', handleResize);
                return () => {
                    window.removeEventListener('resize', handleResize);
                    myChart.dispose();
                };
            }, [solution, project]);

            return <div ref={chartRef} style={{ width: '100%', height: '400px' }}></div>;
        };

        const ObjectiveChart = ({ history, currentIndex }) => {
            const chartRef = useRef(null);

            useEffect(() => {
                if (!chartRef.current || !history) return;
                const myChart = echarts.init(chartRef.current, 'dark');
                myChart.setOption({ backgroundColor: 'transparent' });

                const maxDurations = history.map(h => parseDuration(h.maxDuration));
                const priorityCosts = history.map(h => h.priorityCost);
                const xData = history.map((_, i) => i);

                const option = {
                    tooltip: { trigger: 'axis' },
                    legend: { data: ['Max Duration (m)', 'Priority Cost'] },
                    grid: { height: 200, bottom: 30 },
                    xAxis: { type: 'category', data: xData },
                    yAxis: [{ type: 'value', name: 'Duration (m)' }, { type: 'value', name: 'Cost' }],
                    series: [
                        { 
                            name: 'Max Duration (m)', 
                            type: 'line', 
                            data: maxDurations, 
                            itemStyle: { color: '#fac858' },
                            markLine: {
                                symbol: ['none', 'none'],
                                label: { show: false },
                                lineStyle: { color: '#ee6666', type: 'solid' },
                                data: [{ xAxis: currentIndex }]
                            } 
                        },
                        { 
                            name: 'Priority Cost', 
                            type: 'line', 
                            yAxisIndex: 1, 
                            data: priorityCosts,
                            itemStyle: { color: '#5470c6' }
                        }
                    ]
                };

                myChart.setOption(option);

                const handleResize = () => myChart.resize();
                window.addEventListener('resize', handleResize);
                return () => {
                    window.removeEventListener('resize', handleResize);
                    myChart.dispose();
                };
            }, [history, currentIndex]);

            return <div ref={chartRef} style={{ width: '100%', height: '300px' }}></div>;
        };

        const App = () => {
            const data = window.BELLUM_DATA;
            const [currentIndex, setCurrentIndex] = useState(data && data.solutions ? data.solutions.length - 1 : 0);

            if (!data || !data.solutions || data.solutions.length === 0) {
                return <div>No solution data available.</div>;
            }

            const activeProject = data.solutions[currentIndex];
            const activeStats = data.solutionHistory[currentIndex];
            const maxIdx = data.solutions.length - 1;
            
            const numAssigned = activeProject.tasks.filter(t => t.employee != null).length;
            const isScheduled = numAssigned === activeProject.tasks.length;
            const status = isScheduled ? 'SCHEDULED' : (numAssigned > 0 ? 'PARTIAL' : 'NONE');

            return (
                <div className="dashboard">
                    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <h2>Solution Navigation</h2>
                        <div style={{ display: 'flex', gap: '20px', alignItems: 'center' }}>
                            <input 
                                type="range" 
                                min="0" 
                                max={maxIdx} 
                                value={currentIndex} 
                                onChange={e => setCurrentIndex(parseInt(e.target.value, 10))} 
                                style={{ flex: 1 }}
                            />
                            <div style={{ fontWeight: 'bold', minWidth: '150px', textAlign: 'right' }}>
                                Solution: {currentIndex + 1} / {maxIdx + 1}
                            </div>
                        </div>
                    </div>

                    <div className="card">
                        <h2>Gantt Chart (Worker x Task)</h2>
                        <GanttChart solution={activeStats} project={activeProject} />
                    </div>

                    <div className="row">
                        <div className="card col">
                            <h2>Current Solution Info</h2>
                            <table>
                                <tbody>
                                    <tr><th>Optimal Found</th><td>{activeStats.optimal ? 'Yes' : 'No'}</td></tr>
                                    <tr><th>Valid Schedule</th><td>{activeStats.valid ? 'Yes' : 'No'}</td></tr>
                                    <tr><th>Scheduling Status</th><td>{status}</td></tr>
                                    <tr><th>Max Duration</th><td>{activeStats.maxDuration}</td></tr>
                                    <tr><th>Priority Cost</th><td>{activeStats.priorityCost}</td></tr>
                                </tbody>
                            </table>
                        </div>

                        <div className="card col">
                            <h2>Solver Statistics</h2>
                            <table>
                                <tbody>
                                    {Object.entries(data.solverStats)
                                        .filter(([k, v]) => typeof v !== 'object' && k !== 'type')
                                        .map(([k, v]) => (
                                            <tr key={k}><th>{k.charAt(0).toUpperCase() + k.slice(1)}</th><td>{v}</td></tr>
                                        ))
                                    }
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div className="card">
                        <h2>Objective Function Evolution</h2>
                        <ObjectiveChart history={data.solutionHistory} currentIndex={currentIndex} />
                    </div>
                </div>
            );
        };

        const root = ReactDOM.createRoot(document.getElementById('root'));
        root.render(<App />);
    </script>
</body>
</html>"""
}
